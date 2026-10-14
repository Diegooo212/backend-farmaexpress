package cl.farmaexpress.bff;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import cl.farmaexpress.bff.catalog.CatalogClient;
import cl.farmaexpress.bff.catalog.CatalogClient.Medicamento;
import cl.farmaexpress.bff.eventos.EventosListener;
import cl.farmaexpress.bff.eventos.EventosListener.PagoAprobado;
import cl.farmaexpress.bff.inventory.InventoryClient;
import cl.farmaexpress.bff.inventory.InventoryClient.Reserva;
import cl.farmaexpress.bff.inventory.InventoryClient.Sucursal;
import cl.farmaexpress.bff.payments.PaymentsClient;
import cl.farmaexpress.bff.payments.PaymentsClient.PagoIniciado;
import cl.farmaexpress.bff.payments.PaymentsClient.Redireccion;

/** El personal prepara y entrega los pedidos pagados; el cliente ve el seguimiento. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@Transactional
class GestionPedidosApiTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	EventosListener eventos;

	@MockitoBean
	CatalogClient catalog;

	@MockitoBean
	InventoryClient inventory;

	@MockitoBean
	PaymentsClient payments;

	@BeforeEach
	void servicios() {
		given(catalog.listar()).willReturn(List.of(new Medicamento(1L, "MED-001", "Paracetamol 500mg", 1990, 120)));
		String expira = Instant.now().plusSeconds(900).toString();
		given(inventory.reservar(anyString(), eq(2L), anyList(), anyString())).willAnswer(i -> new Reserva(10L,
				i.getArgument(0), "ACTIVA", new Sucursal(2L, "CENTRO", "Farmacia Centro", "x", "Santiago", "FARMACIA"), expira));
		given(inventory.reservar(anyString(), isNull(), anyList(), anyString())).willAnswer(i -> new Reserva(11L,
				i.getArgument(0), "ACTIVA", new Sucursal(1L, "CENTRAL", "Almacén Central", "x", "Pudahuel", "CENTRAL"), expira));
		given(payments.iniciar(anyLong(), anyLong(), anyInt(), anyString(), anyString(), anyString()))
			.willAnswer(i -> new PagoIniciado(500L, i.getArgument(0), "PENDIENTE", "WEBPAY", "REAL",
					new Redireccion("GET", "https://pago", Map.of())));
	}

	private static RequestPostProcessor usuario(String email, String rol) {
		return jwt().jwt(j -> j.subject("sub-" + email).claim("oid", "oid-" + email).claim("email", email)
				.claim("name", email).claim("scp", "access_as_user").claim("roles", List.of(rol)).tokenValue("token-" + email))
			.authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"), new SimpleGrantedAuthority("ROLE_" + rol));
	}

	private static final RequestPostProcessor CLIENTE = usuario("cliente@test.cl", "Cliente");
	private static final RequestPostProcessor OPERADOR = usuario("qf@farmaexpress.cl", "Operador");

	private long pedidoPagado(String entrega) throws Exception {
		mvc.perform(put("/api/bff/cart").with(CLIENTE).contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"id\":1,\"cantidad\":2}]}"))
			.andExpect(status().isOk());
		String body = "RETIRO".equals(entrega) ? "{\"entrega\":\"RETIRO\",\"sucursalId\":2,\"metodoPago\":\"WEBPAY\"}"
				: "{\"entrega\":\"DESPACHO\",\"direccion\":\"Av. Siempre Viva 742\",\"metodoPago\":\"WEBPAY\"}";
		String json = mvc.perform(post("/api/bff/orders").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(json, "$.pedido.id")).longValue();
		eventos.pagoAprobado(new PagoAprobado("ev", 500L, id, 10L, "oid-cliente@test.cl", 3980, "WEBPAY", "1", Instant.now()));
		return id;
	}

	private ResultActions avanzar(long id, String estado) throws Exception {
		return mvc.perform(put("/api/bff/gestion/pedidos/" + id + "/estado").with(OPERADOR)
			.contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"" + estado + "\"}"));
	}

	@Test
	void elPanelEsSoloParaElPersonal() throws Exception {
		mvc.perform(get("/api/bff/gestion/pedidos")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/bff/gestion/pedidos").with(CLIENTE)).andExpect(status().isForbidden());
		mvc.perform(get("/api/bff/gestion/pedidos").with(OPERADOR)).andExpect(status().isOk());
	}

	@Test
	void retiroEnFarmaciaSePreparaQuedaListoYSeEntrega() throws Exception {
		long id = pedidoPagado("RETIRO");

		mvc.perform(get("/api/bff/gestion/pedidos").with(OPERADOR))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].pedido.estado").value("PAGADO"))
			.andExpect(jsonPath("$[0].email").value("cliente@test.cl"));

		avanzar(id, "EN_PREPARACION").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EN_PREPARACION"));
		// Un retiro no va "en camino".
		avanzar(id, "EN_CAMINO").andExpect(status().isConflict());
		avanzar(id, "LISTO_RETIRO").andExpect(status().isOk());
		avanzar(id, "ENTREGADO").andExpect(status().isOk());

		mvc.perform(get("/api/bff/orders/" + id).with(CLIENTE))
			.andExpect(jsonPath("$.estado").value("ENTREGADO"))
			.andExpect(jsonPath("$.historial[*].estado").value(org.hamcrest.Matchers.contains("PENDIENTE_PAGO", "PAGADO",
					"EN_PREPARACION", "LISTO_RETIRO", "ENTREGADO")))
			.andExpect(jsonPath("$.historial[2].por").value("qf@farmaexpress.cl"));
		mvc.perform(get("/api/bff/gestion/pedidos").with(OPERADOR)).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/bff/gestion/pedidos").param("entregados", "true").with(OPERADOR))
			.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void despachoVaEnCaminoYNoPuedeSaltarseLaPreparacion() throws Exception {
		long id = pedidoPagado("DESPACHO");

		avanzar(id, "ENTREGADO").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("no puede pasar a ENTREGADO")));
		avanzar(id, "EN_PREPARACION").andExpect(status().isOk());
		avanzar(id, "LISTO_RETIRO").andExpect(status().isConflict());
		avanzar(id, "EN_CAMINO").andExpect(status().isOk());
		avanzar(id, "ENTREGADO").andExpect(status().isOk());
	}
}
