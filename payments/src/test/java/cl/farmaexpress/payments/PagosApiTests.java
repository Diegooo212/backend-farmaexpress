package cl.farmaexpress.payments;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;

import cl.farmaexpress.payments.domain.MetodoPago;
import cl.farmaexpress.payments.service.SimulacionFallas;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/** Flujo de pago con los proveedores en modo simulador (sin internet) y el circuit breaker real. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@Import(EntraDePrueba.class)
class PagosApiTests {

	private static final String BASE = "/api/payments";

	@Autowired
	MockMvc mvc;

	@Autowired
	CircuitBreakerRegistry circuitos;

	@Autowired
	SimulacionFallas fallas;

	private static RequestPostProcessor usuario(String oid, String rol) {
		return jwt().jwt(j -> j.subject("sub-" + oid).claim("oid", oid).claim("scp", "access_as_user")
				.claim("roles", List.of(rol)))
			.authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"), new SimpleGrantedAuthority("ROLE_" + rol));
	}

	private static final RequestPostProcessor CLIENTE = usuario("oid-cliente", "Cliente");
	private static final RequestPostProcessor OTRO = usuario("oid-otro", "Cliente");
	private static final RequestPostProcessor OPERADOR = usuario("oid-operador", "Operador");
	private static final RequestPostProcessor ADMIN = usuario("oid-admin", "Admin");

	@AfterEach
	void restaurarProveedores() {
		for (MetodoPago m : MetodoPago.values()) {
			fallas.cambiar(m, false);
			circuitos.circuitBreaker(m.ruta()).reset();
		}
	}

	private ResultActions iniciar(long pedidoId, String metodo) throws Exception {
		String body = "{\"pedidoId\":%d,\"reservaId\":%d,\"monto\":5980,\"metodo\":\"%s\"}".formatted(pedidoId, pedidoId + 100,
				metodo);
		return mvc.perform(post(BASE).with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** Inicia un pago y devuelve {pagoId, tokenDelSimulador}. */
	private String[] iniciarSimulado(long pedidoId, String metodo) throws Exception {
		String json = iniciar(pedidoId, metodo).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String url = JsonPath.read(json, "$.redireccion.url");
		String token = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("token");
		return new String[] { String.valueOf(((Number) JsonPath.read(json, "$.pagoId")).longValue()), token };
	}

	private ResultActions volver(String metodo, String pagoId, String token, String resultado) throws Exception {
		return mvc.perform(get(BASE + "/retorno/" + metodo).param("pago", pagoId).param("token", token)
			.param("resultado", resultado));
	}

	@Test
	void losMediosDePagoSonPublicosYMuestranSuCircuito() throws Exception {
		mvc.perform(get(BASE + "/metodos"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].codigo").value("WEBPAY"))
			.andExpect(jsonPath("$[0].disponible").value(true))
			.andExpect(jsonPath("$[0].circuito").value("CLOSED"))
			.andExpect(jsonPath("$[2].modo").value("SIMULADO"));
	}

	@Test
	void iniciarPagoExigeTokenYDatosValidos() throws Exception {
		mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
		mvc.perform(post(BASE).with(CLIENTE).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pedidoId\":1,\"monto\":0,\"metodo\":\"KHIPU\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void pagoAprobadoRedirigeAlResultadoYNoSePuedePagarDosVeces() throws Exception {
		String[] pago = iniciarSimulado(10, "MERCADOPAGO");

		volver("mercadopago", pago[0], pago[1], "aprobado")
			.andExpect(status().isSeeOther())
			.andExpect(header().string("Location", "http://localhost:5173/pago/resultado?pedido=10"));

		mvc.perform(get(BASE + "/" + pago[0]).with(CLIENTE))
			.andExpect(jsonPath("$.estado").value("APROBADO"))
			.andExpect(jsonPath("$.referencia").value("SIM-" + pago[0]));
		// Volver a enviar el retorno (recargar la página) no cambia nada.
		volver("mercadopago", pago[0], pago[1], "rechazado").andExpect(status().isSeeOther());
		mvc.perform(get(BASE + "/" + pago[0]).with(CLIENTE)).andExpect(jsonPath("$.estado").value("APROBADO"));

		iniciar(10, "KHIPU").andExpect(status().isConflict());
	}

	@Test
	void unPagoRechazadoSePuedeReintentarConOtroMedio() throws Exception {
		String[] pago = iniciarSimulado(11, "KHIPU");
		volver("khipu", pago[0], pago[1], "rechazado").andExpect(status().isSeeOther());
		mvc.perform(get(BASE + "/" + pago[0]).with(CLIENTE))
			.andExpect(jsonPath("$.estado").value("RECHAZADO"))
			.andExpect(jsonPath("$.detalle").value(containsString("rechazado")));

		iniciar(11, "WEBPAY").andExpect(status().isCreated());
		mvc.perform(get(BASE).param("pedidoId", "11").with(CLIENTE)).andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void unRetornoConTokenFalsoNoCambiaElPago() throws Exception {
		String[] pago = iniciarSimulado(12, "WEBPAY");

		volver("webpay", pago[0], "token-inventado", "aprobado")
			.andExpect(header().string("Location", containsString("error=verificacion")));
		mvc.perform(get(BASE + "/" + pago[0]).with(CLIENTE)).andExpect(jsonPath("$.estado").value("PENDIENTE"));

		// Pago que no existe, o que vuelve por otro proveedor.
		volver("webpay", "999999", pago[1], "aprobado")
			.andExpect(header().string("Location", containsString("error=pago-desconocido")));
		volver("khipu", pago[0], pago[1], "aprobado")
			.andExpect(header().string("Location", containsString("error=pago-desconocido")));
	}

	@Test
	void unPagoAjenoNoSeVe() throws Exception {
		String[] pago = iniciarSimulado(13, "KHIPU");
		mvc.perform(get(BASE + "/" + pago[0]).with(OTRO)).andExpect(status().isNotFound());
		mvc.perform(get(BASE + "/" + pago[0]).with(OPERADOR)).andExpect(status().isOk());
	}

	@Test
	void simularUnaCaidaEsSoloParaAdmin() throws Exception {
		String body = "{\"metodo\":\"KHIPU\",\"caido\":true}";
		mvc.perform(post(BASE + "/simulacion").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden());
		mvc.perform(post(BASE + "/simulacion").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk());
	}

	@Test
	void elCircuitBreakerSeAbreConFallasSeguidasYSeRecuperaSolo() throws Exception {
		fallas.cambiar(MetodoPago.KHIPU, true);

		// Tres llamadas fallidas: el proveedor "no responde".
		for (int i = 0; i < 3; i++) {
			iniciar(20 + i, "KHIPU")
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value(containsString("Khipu no respondió")));
		}

		// El circuito de Khipu quedó ABIERTO; Webpay y Mercado Pago siguen disponibles.
		mvc.perform(get(BASE + "/metodos"))
			.andExpect(jsonPath("$[2].codigo").value("KHIPU"))
			.andExpect(jsonPath("$[2].disponible").value(false))
			.andExpect(jsonPath("$[2].circuito").value("OPEN"))
			.andExpect(jsonPath("$[0].disponible").value(true));

		// Con el circuito abierto ni siquiera se intenta llamar al proveedor.
		iniciar(30, "KHIPU")
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.detail").value(containsString("no está disponible en este momento")));
		iniciar(31, "WEBPAY").andExpect(status().isCreated());

		// Khipu vuelve; pasado el tiempo de espera el circuito deja pasar una prueba y se cierra.
		fallas.cambiar(MetodoPago.KHIPU, false);
		Thread.sleep(700);
		iniciar(32, "KHIPU").andExpect(status().isCreated());
		mvc.perform(get(BASE + "/metodos")).andExpect(jsonPath("$[2].circuito").value("CLOSED"));
	}
}
