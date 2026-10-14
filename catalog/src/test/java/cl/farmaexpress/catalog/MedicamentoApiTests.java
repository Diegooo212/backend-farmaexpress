package cl.farmaexpress.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
import org.springframework.transaction.annotation.Transactional;

import cl.farmaexpress.catalog.domain.MedicamentoRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@Import(EntraDePrueba.class)
@Transactional
class MedicamentoApiTests {

	private static final String BASE = "/api/catalog/medicamentos";
	private static final String NUEVO = "{\"sku\":\"MED-100\",\"nombre\":\"Cetirizina 10mg\",\"precio\":2990,\"stock\":30}";

	@Autowired
	MockMvc mvc;

	@Autowired
	MedicamentoRepository repository;

	@Autowired
	cl.farmaexpress.catalog.eventos.InventarioListener inventario;

	private static RequestPostProcessor como(String rol) {
		return jwt().jwt(j -> j.subject(rol.toLowerCase() + "@test.cl"))
			.authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"), new SimpleGrantedAuthority("ROLE_" + rol));
	}

	private ResultActions crearConToken(String token) throws Exception {
		return mvc.perform(post(BASE).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content(NUEVO));
	}

	@Test
	void listarEsPublicoYTraeLosDatosIniciales() throws Exception {
		mvc.perform(get(BASE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(21)))
			.andExpect(jsonPath("$[0].sku").value("MED-001"))
			.andExpect(jsonPath("$[0].nombre").value("Paracetamol 500mg"))
			.andExpect(jsonPath("$[0].precio").value(1990))
			.andExpect(jsonPath("$[0].stock").value(120))
			.andExpect(jsonPath("$[0].categoria").value("Dolor y fiebre"))
			.andExpect(jsonPath("$[0].categoriaSlug").value("dolor-y-fiebre"))
			.andExpect(jsonPath("$[0].presentacion").value("Caja 16 comprimidos"))
			.andExpect(jsonPath("$[0].requiereReceta").value(false))
			.andExpect(jsonPath("$[2].nombre").value("Amoxicilina 500mg"))
			.andExpect(jsonPath("$[2].requiereReceta").value(true));
	}

	@Test
	void lasCategoriasSonPublicasYCuentanSusProductos() throws Exception {
		mvc.perform(get("/api/catalog/categorias"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(8)))
			.andExpect(jsonPath("$[0].slug").value("dolor-y-fiebre"))
			.andExpect(jsonPath("$[0].productos").value(4))
			.andExpect(jsonPath("$[4].nombre").value("Antibióticos"))
			.andExpect(jsonPath("$[4].productos").value(2));
	}

	@Test
	void crearConFichaCompleta() throws Exception {
		String body = """
				{"sku":"MED-200","nombre":"Naproxeno 550mg","precio":3990,"categoriaId":1,"laboratorio":"FarmaGen",
				 "principioActivo":"Naproxeno","presentacion":"Caja 10 comprimidos","requiereReceta":true,
				 "descripcion":"Antiinflamatorio.","imagenUrl":"https://ejemplo.cl/naproxeno.png"}""";
		mvc.perform(post(BASE).with(como("Admin")).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.stock").value(0))
			.andExpect(jsonPath("$.categoria").value("Dolor y fiebre"))
			.andExpect(jsonPath("$.requiereReceta").value(true))
			.andExpect(jsonPath("$.imagenUrl").value("https://ejemplo.cl/naproxeno.png"));

		mvc.perform(post(BASE).with(como("Admin")).contentType(MediaType.APPLICATION_JSON)
				.content(body.replace("MED-200", "MED-201").replace("\"categoriaId\":1", "\"categoriaId\":99")))
			.andExpect(status().isBadRequest());
		mvc.perform(post(BASE).with(como("Admin")).contentType(MediaType.APPLICATION_JSON)
				.content(body.replace("MED-200", "MED-202").replace("https://", "http://")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void crearRequiereRolAdmin() throws Exception {
		mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(NUEVO))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401));
		mvc.perform(post(BASE).with(como("Cliente")).contentType(MediaType.APPLICATION_JSON).content(NUEVO))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value(containsString("rol o el scope")));
		mvc.perform(post(BASE).with(como("Admin")).contentType(MediaType.APPLICATION_JSON).content(NUEVO))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.sku").value("MED-100"));
	}

	@Test
	void crearConSkuRepetidoDevuelve409() throws Exception {
		String body = "{\"sku\":\"med-001\",\"nombre\":\"Otro\",\"precio\":100,\"stock\":1}";
		mvc.perform(post(BASE).with(como("Admin")).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("Ya existe")));
	}

	@Test
	void crearConDatosInvalidosDevuelve400ConMensaje() throws Exception {
		String body = "{\"sku\":\"\",\"nombre\":\"X\",\"precio\":-5,\"stock\":1}";
		mvc.perform(post(BASE).with(como("Admin")).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("El precio no puede ser negativo.")));
	}

	@Test
	void actualizarPrecioPeroNoElStockQueEsDelInventario() throws Exception {
		String body = "{\"sku\":\"MED-002\",\"nombre\":\"Ibuprofeno 400mg\",\"precio\":2590,\"stock\":50,\"categoriaId\":1}";
		mvc.perform(put(BASE + "/2").with(como("Admin")).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.precio").value(2590))
			.andExpect(jsonPath("$.stock").value(8));
		mvc.perform(put(BASE + "/999").with(como("Admin")).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isNotFound());
	}

	@Test
	void eliminarEsSoloParaAdminYQuitaElProducto() throws Exception {
		mvc.perform(delete(BASE + "/6")).andExpect(status().isUnauthorized());
		mvc.perform(delete(BASE + "/6").with(como("Cliente"))).andExpect(status().isForbidden());
		mvc.perform(delete(BASE + "/6").with(como("Admin"))).andExpect(status().isNoContent());

		mvc.perform(get(BASE + "/6")).andExpect(status().isNotFound());
		mvc.perform(get(BASE)).andExpect(jsonPath("$", hasSize(20)));
		mvc.perform(delete(BASE + "/6").with(como("Admin"))).andExpect(status().isNotFound());
	}

	@Test
	void descontarStockDeUnaCompra() throws Exception {
		String body = "{\"items\":[{\"id\":1,\"cantidad\":3},{\"id\":4,\"cantidad\":5},{\"id\":1,\"cantidad\":2}]}";
		mvc.perform(post(BASE + "/descontar-stock").with(como("Cliente")).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].stock").value(115))
			.andExpect(jsonPath("$[1].stock").value(40));
	}

	@Test
	void sinStockSuficienteNoSeDescuentaNada() throws Exception {
		// Paracetamol tiene stock, Ibuprofeno solo 8: la compra completa se rechaza.
		String body = "{\"items\":[{\"id\":1,\"cantidad\":1},{\"id\":2,\"cantidad\":9}]}";
		mvc.perform(post(BASE + "/descontar-stock").with(como("Cliente")).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("Ibuprofeno 400mg")));
		assertThat(repository.findById(1L).orElseThrow().getStock()).isEqualTo(120);
		assertThat(repository.findById(2L).orElseThrow().getStock()).isEqualTo(8);
	}

	@Test
	void descontarStockRequiereSesion() throws Exception {
		mvc.perform(post(BASE + "/descontar-stock").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"id\":1,\"cantidad\":1}]}"))
			.andExpect(status().isUnauthorized());
	}

	// ---- Tokens firmados de verdad: se prueba la validación completa del JWT de Entra ID ----

	@Test
	void jwtDeEntraValidoConRolAdminPuedeCrear() throws Exception {
		crearConToken(EntraDePrueba.token(Map.of("roles", List.of("Admin")))).andExpect(status().isCreated());
	}

	@Test
	void rolAdministradorDeLaAppRegistrationPuedeCrear() throws Exception {
		// En la App Registration los roles se llaman "Administrador", "Operador" y "Cliente".
		crearConToken(EntraDePrueba.token(Map.of("roles", List.of("Administrador")))).andExpect(status().isCreated());
		crearConToken(EntraDePrueba.token(Map.of("roles", List.of("Operador")))).andExpect(status().isForbidden());
		crearConToken(EntraDePrueba.token(Map.of("roles", List.of("Cliente")))).andExpect(status().isForbidden());
	}

	@Test
	void jwtValidoSinRolAdminDa403() throws Exception {
		crearConToken(EntraDePrueba.token(Map.of())).andExpect(status().isForbidden());
	}

	@Test
	void jwtSinElScopeDeLaApiDa403() throws Exception {
		crearConToken(EntraDePrueba.token(Map.of("roles", List.of("Admin"), "scp", "User.Read")))
			.andExpect(status().isForbidden())
			.andExpect(header().string("WWW-Authenticate", containsString("insufficient_scope")));
	}

	@Test
	void jwtConFirmaAlteradaDa401() throws Exception {
		String token = EntraDePrueba.token(Map.of("roles", List.of("Admin")));
		crearConToken(token.substring(0, token.length() - 4) + "AAAA")
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
	}

	@Test
	void aceptaTokensV1DelMismoTenant() throws Exception {
		// Si la App Registration emite tokens v1, el iss es https://sts.windows.net/<tenant>/ y aud = api://<client-id>.
		crearConToken(EntraDePrueba.token(Map.of("roles", List.of("Administrador"),
				"iss", EntraDePrueba.ISSUER_V1, "aud", List.of("api://" + EntraDePrueba.API_CLIENT_ID))))
			.andExpect(status().isCreated());
	}

	@Test
	void jwtDeOtroEmisorDa401() throws Exception {
		crearConToken(EntraDePrueba.token(Map.of("roles", List.of("Admin"),
				"iss", "https://otro-tenant.ciamlogin.com/otro-tenant/v2.0")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void jwtParaOtraApiDa401() throws Exception {
		crearConToken(EntraDePrueba.token(Map.of("roles", List.of("Admin"), "aud", List.of("otra-api"))))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void jwtVencidoDa401() throws Exception {
		Instant antes = Instant.now().minusSeconds(7200);
		crearConToken(EntraDePrueba.token(Map.of("roles", List.of("Admin"),
				"iat", antes, "nbf", antes, "exp", antes.plusSeconds(600))))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void elStockLoInformaElInventarioPorRabbitMq() throws Exception {
		inventario.stockCambiado(new cl.farmaexpress.catalog.eventos.InventarioListener.StockCambiado("ev-1",
				List.of(new cl.farmaexpress.catalog.eventos.InventarioListener.StockProducto(1L, 77, 101),
						new cl.farmaexpress.catalog.eventos.InventarioListener.StockProducto(999L, 5, 5)),
				java.time.Instant.now()));

		mvc.perform(get(BASE + "/1"))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.stock").value(77))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.stockTotal").value(101));
	}
}
