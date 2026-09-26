package cl.farmaexpress.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import cl.farmaexpress.bff.auth.Rol;
import cl.farmaexpress.bff.auth.UsuarioRepository;
import cl.farmaexpress.bff.catalog.CatalogClient;
import cl.farmaexpress.bff.prescriptions.PrescriptionsClient;

/**
 * Validación del JWT de Entra ID en el BFF (EP1): firma, emisor, audiencia, vigencia, scope y rol,
 * con respuestas 200 / 401 / 403 coherentes. Los tokens se firman de verdad con una llave de prueba.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@Import(EntraDePrueba.class)
@Transactional
class SeguridadApiTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	UsuarioRepository usuarios;

	@MockitoBean
	CatalogClient catalog;

	@MockitoBean
	PrescriptionsClient prescriptions;

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder request, Map<String, Object> claims) {
		return request.header("Authorization", "Bearer " + EntraDePrueba.token(claims));
	}

	private static ResponseEntity<byte[]> json(String cuerpo) {
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(cuerpo.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void meRegistraElPerfilConLosClaimsDelToken() throws Exception {
		mvc.perform(conToken(get("/api/bff/auth/me"), Map.of("oid", "oid-carla", "name", "Carla Muñoz",
				"email", "Carla@DuocUC.cl", "roles", List.of("Operador"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("oid-carla"))
			.andExpect(jsonPath("$.nombre").value("Carla Muñoz"))
			.andExpect(jsonPath("$.email").value("carla@duocuc.cl"))
			.andExpect(jsonPath("$.roles[0]").value("Operador"))
			.andExpect(jsonPath("$.scopes[0]").value("access_as_user"));

		var guardado = usuarios.findByEntraOid("oid-carla").orElseThrow();
		assertThat(guardado.getRol()).isEqualTo(Rol.Operador);

		// Si en Entra le quitan el App Role, en su siguiente ingreso vuelve a ser Cliente.
		mvc.perform(conToken(get("/api/bff/auth/me"), Map.of("oid", "oid-carla")))
			.andExpect(jsonPath("$.roles[0]").value("Cliente"));
	}

	@Test
	void sinTokenDa401ConMensaje() throws Exception {
		mvc.perform(get("/api/bff/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.detail").value(containsString("Microsoft Entra ID")));
	}

	@Test
	void tokenInvalidoDa401() throws Exception {
		String bueno = EntraDePrueba.token(Map.of());
		Instant antes = Instant.now().minusSeconds(7200);

		// Firma alterada
		mvc.perform(get("/api/bff/auth/me").header("Authorization", "Bearer " + bueno.substring(0, bueno.length() - 4) + "AAAA"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
		// Otro emisor (otro tenant)
		mvc.perform(conToken(get("/api/bff/auth/me"), Map.of("iss", "https://otro.ciamlogin.com/otro/v2.0")))
			.andExpect(status().isUnauthorized());
		// Emitido para otra API
		mvc.perform(conToken(get("/api/bff/auth/me"), Map.of("aud", List.of("00000003-0000-0000-c000-000000000000"))))
			.andExpect(status().isUnauthorized());
		// Vencido
		mvc.perform(conToken(get("/api/bff/auth/me"), Map.of("iat", antes, "nbf", antes, "exp", antes.plusSeconds(600))))
			.andExpect(status().isUnauthorized());
		// No es un JWT
		mvc.perform(get("/api/bff/auth/me").header("Authorization", "Bearer hola"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenSinElScopeDeLaApiDa403() throws Exception {
		mvc.perform(conToken(get("/api/bff/cart"), Map.of("scp", "openid profile")))
			.andExpect(status().isForbidden())
			.andExpect(header().string("WWW-Authenticate", containsString("insufficient_scope")))
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void catalogoEsPublicoPeroEditarloEsSoloParaAdmin() throws Exception {
		given(catalog.reenviar(HttpMethod.GET, "", null, null)).willReturn(json("[{\"id\":1}]"));
		given(catalog.reenviar(eq(HttpMethod.POST), eq(""), anyString(), anyString())).willReturn(json("{\"id\":7}"));
		String nuevo = "{\"sku\":\"MED-7\",\"nombre\":\"X\",\"precio\":1,\"stock\":1}";

		mvc.perform(get("/api/bff/catalog/medicamentos")).andExpect(status().isOk());
		mvc.perform(post("/api/bff/catalog/medicamentos").contentType(MediaType.APPLICATION_JSON).content(nuevo))
			.andExpect(status().isUnauthorized());
		mvc.perform(conToken(post("/api/bff/catalog/medicamentos"), Map.of()).contentType(MediaType.APPLICATION_JSON).content(nuevo))
			.andExpect(status().isForbidden());
		mvc.perform(conToken(post("/api/bff/catalog/medicamentos"), Map.of("roles", List.of("Admin")))
				.contentType(MediaType.APPLICATION_JSON).content(nuevo))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(7));
	}

	@Test
	void eliminarProductoEsSoloParaAdmin() throws Exception {
		given(catalog.reenviar(eq(HttpMethod.DELETE), eq("/6"), isNull(), anyString()))
			.willReturn(ResponseEntity.noContent().build());

		mvc.perform(delete("/api/bff/catalog/medicamentos/6")).andExpect(status().isUnauthorized());
		mvc.perform(conToken(delete("/api/bff/catalog/medicamentos/6"), Map.of("roles", List.of("Operador"))))
			.andExpect(status().isForbidden());
		mvc.perform(conToken(delete("/api/bff/catalog/medicamentos/6"), Map.of("roles", List.of("Administrador"))))
			.andExpect(status().isNoContent());
	}

	@Test
	void cambiarEstadoDeRecetaEsParaOperadorOAdmin() throws Exception {
		given(prescriptions.reenviar(eq(HttpMethod.PUT), eq("/5/status"), anyString(), anyString()))
			.willReturn(json("{\"id\":5,\"status\":\"VALIDADA\"}"));
		String cuerpo = "{\"status\":\"VALIDADA\"}";

		mvc.perform(conToken(put("/api/bff/prescriptions/5/status"), Map.of()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
			.andExpect(status().isForbidden());
		mvc.perform(conToken(put("/api/bff/prescriptions/5/status"), Map.of("roles", List.of("Operador")))
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("VALIDADA"));
	}

	@Test
	void elBffReenviaElMismoTokenDeEntraAlMicroservicio() throws Exception {
		String token = EntraDePrueba.token(Map.of());
		given(prescriptions.reenviar(HttpMethod.GET, "", null, token)).willReturn(json("[]"));

		mvc.perform(get("/api/bff/prescriptions").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		org.mockito.Mockito.verify(prescriptions).reenviar(HttpMethod.GET, "", null, token);
	}

	@Test
	void corsPermiteAlFrontendLocal() throws Exception {
		mvc.perform(options("/api/bff/cart")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "authorization"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
		mvc.perform(options("/api/bff/cart")
				.header("Origin", "http://sitio-malicioso.com")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isForbidden());
	}

	@SuppressWarnings("unused")
	private static Object cualquiera() {
		return any();
	}
}
