package cl.farmaexpress.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
			.andExpect(jsonPath("$", hasSize(6)))
			.andExpect(jsonPath("$[0].sku").value("MED-001"))
			.andExpect(jsonPath("$[0].nombre").value("Paracetamol 500mg"))
			.andExpect(jsonPath("$[0].precio").value(1990))
			.andExpect(jsonPath("$[0].stock").value(120));
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
	void actualizarPrecioYStock() throws Exception {
		String body = "{\"sku\":\"MED-002\",\"nombre\":\"Ibuprofeno 400mg\",\"precio\":2590,\"stock\":50}";
		mvc.perform(put(BASE + "/2").with(como("Admin")).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.precio").value(2590))
			.andExpect(jsonPath("$.stock").value(50));
		mvc.perform(put(BASE + "/999").with(como("Admin")).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isNotFound());
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
}
