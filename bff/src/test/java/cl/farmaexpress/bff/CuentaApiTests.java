package cl.farmaexpress.bff;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** Mi cuenta: teléfono y direcciones guardadas, siempre de la propia cuenta. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@Transactional
class CuentaApiTests {

	@Autowired
	MockMvc mvc;

	private static RequestPostProcessor como(String email) {
		return jwt().jwt(j -> j.subject("sub-" + email).claim("oid", "oid-" + email).claim("email", email)
				.claim("name", "Persona " + email).claim("scp", "access_as_user"))
			.authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"));
	}

	private static final RequestPostProcessor ANA = como("ana@test.cl");
	private static final RequestPostProcessor BETO = como("beto@test.cl");

	private ResultActions agregar(RequestPostProcessor quien, String alias, boolean predeterminada) throws Exception {
		return mvc.perform(post("/api/bff/account/direcciones").with(quien).contentType(MediaType.APPLICATION_JSON)
			.content("{\"alias\":\"%s\",\"calle\":\"Av. Providencia 1234\",\"depto\":\"501\",\"comuna\":\"Providencia\",\"predeterminada\":%s}"
				.formatted(alias, predeterminada)));
	}

	@Test
	void elPerfilMuestraLosDatosDeMicrosoftYGuardaElTelefono() throws Exception {
		mvc.perform(get("/api/bff/account/perfil")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/bff/account/perfil").with(ANA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nombre").value("Persona ana@test.cl"))
			.andExpect(jsonPath("$.email").value("ana@test.cl"))
			.andExpect(jsonPath("$.rol").value("Cliente"));

		mvc.perform(put("/api/bff/account/perfil").with(ANA).contentType(MediaType.APPLICATION_JSON)
				.content("{\"telefono\":\"+56 9 1234 5678\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.telefono").value("+56 9 1234 5678"));
		mvc.perform(put("/api/bff/account/perfil").with(ANA).contentType(MediaType.APPLICATION_JSON)
				.content("{\"telefono\":\"llamame\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void laPrimeraDireccionQuedaPredeterminadaYSePuedeCambiar() throws Exception {
		agregar(ANA, "Casa", false).andExpect(status().isCreated())
			.andExpect(jsonPath("$.predeterminada").value(true))
			.andExpect(jsonPath("$.completa").value("Av. Providencia 1234, 501, Providencia"));
		String json = agregar(ANA, "Trabajo", true).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long trabajo = ((Number) JsonPath.read(json, "$.id")).longValue();

		mvc.perform(get("/api/bff/account/direcciones").with(ANA))
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].alias").value("Trabajo"))
			.andExpect(jsonPath("$[0].predeterminada").value(true))
			.andExpect(jsonPath("$[1].predeterminada").value(false));

		// Al eliminar la predeterminada, la otra pasa a serlo.
		mvc.perform(delete("/api/bff/account/direcciones/" + trabajo).with(ANA)).andExpect(status().isNoContent());
		mvc.perform(get("/api/bff/account/direcciones").with(ANA))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].alias").value("Casa"))
			.andExpect(jsonPath("$[0].predeterminada").value(true));
	}

	@Test
	void lasDireccionesSonDeCadaCuentaYTienenUnMaximo() throws Exception {
		String json = agregar(ANA, "Casa", false).andReturn().getResponse().getContentAsString();
		long casa = ((Number) JsonPath.read(json, "$.id")).longValue();

		mvc.perform(get("/api/bff/account/direcciones").with(BETO)).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(delete("/api/bff/account/direcciones/" + casa).with(BETO)).andExpect(status().isNotFound());

		for (int i = 2; i <= 5; i++) {
			agregar(ANA, "Dirección " + i, false).andExpect(status().isCreated());
		}
		agregar(ANA, "Sexta", false).andExpect(status().isConflict());
	}

	@Test
	void losFavoritosSonDeCadaCuentaYNoSeDuplican() throws Exception {
		mvc.perform(put("/api/bff/account/favoritos/3").with(ANA)).andExpect(status().isOk());
		mvc.perform(put("/api/bff/account/favoritos/101").with(ANA)).andExpect(status().isOk());
		mvc.perform(put("/api/bff/account/favoritos/3").with(ANA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)));

		mvc.perform(get("/api/bff/account/favoritos").with(BETO)).andExpect(jsonPath("$", hasSize(0)));

		mvc.perform(delete("/api/bff/account/favoritos/3").with(ANA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0]").value(101));
	}
}
