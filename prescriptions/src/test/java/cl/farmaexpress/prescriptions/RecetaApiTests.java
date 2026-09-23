package cl.farmaexpress.prescriptions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@Import(EntraDePrueba.class)
@Transactional
class RecetaApiTests {

	private static final String BASE = "/api/prescriptions";
	/** Cabecera real de un JPEG seguida de relleno. */
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1, 2, 3 };

	@Autowired
	MockMvc mvc;

	/** Como llega el JWT de Entra ID: oid, email, name, scp y roles. */
	private static RequestPostProcessor usuario(String email, String nombre, String rol) {
		return jwt().jwt(j -> j.subject("sub-" + email).claim("oid", "oid-" + email).claim("email", email)
				.claim("name", nombre).claim("scp", "access_as_user").claim("roles", List.of(rol)))
			.authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"), new SimpleGrantedAuthority("ROLE_" + rol));
	}

	private static final RequestPostProcessor PACIENTE = usuario("diego@test.cl", "Diego Tatin", "Cliente");
	private static final RequestPostProcessor OTRO_PACIENTE = usuario("otra@test.cl", "Otra Persona", "Cliente");
	private static final RequestPostProcessor OPERADOR = usuario("op@farmaexpress.cl", "Q.F. Carla Muñoz", "Operador");

	private static String datos(String rut) {
		return """
				{"pacienteNombre":"Diego Tatin","rut":"%s","email":"diego@test.cl","telefono":"+56 9 1111 2222",
				 "tiempoEntrega":"NORMAL","metodoDespacho":"RETIRO_TIENDA","farmacia":"Farmacia Centro",
				 "comentarios":"Genérico si es posible"}""".formatted(rut);
	}

	private ResultActions enviar(RequestPostProcessor quien, String datosJson, MockMultipartFile archivo) throws Exception {
		var request = multipart(BASE).file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
				datosJson.getBytes(StandardCharsets.UTF_8)));
		if (archivo != null) {
			request.file(archivo);
		}
		return mvc.perform(request.with(quien));
	}

	private long crearReceta() throws Exception {
		String json = enviar(PACIENTE, datos("11.111.111-1"), new MockMultipartFile("archivo", "receta.jpg", "image/jpeg", JPEG))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	private ResultActions cambiarEstado(RequestPostProcessor quien, long id, String status, String nota) throws Exception {
		String body = nota == null ? "{\"status\":\"%s\"}".formatted(status)
				: "{\"status\":\"%s\",\"nota\":\"%s\"}".formatted(status, nota);
		return mvc.perform(put(BASE + "/" + id + "/status").with(quien).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void pacienteEnviaRecetaConFoto() throws Exception {
		enviar(PACIENTE, datos("11.111.111-1"), new MockMultipartFile("archivo", "C:\\fotos\\receta.jpg", "image/jpeg", JPEG))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/prescriptions/")))
			.andExpect(jsonPath("$.status").value("INGRESADA"))
			.andExpect(jsonPath("$.cuentaId").value("oid-diego@test.cl"))
			.andExpect(jsonPath("$.cuentaEmail").value("diego@test.cl"))
			.andExpect(jsonPath("$.cuentaNombre").value("Diego Tatin"))
			.andExpect(jsonPath("$.archivoNombre").value("receta.jpg"))
			.andExpect(jsonPath("$.archivoTipo").value("image/jpeg"))
			.andExpect(jsonPath("$.tieneArchivo").value(true))
			.andExpect(jsonPath("$.fechaCreacion").isString())
			.andExpect(jsonPath("$.historial", hasSize(1)))
			.andExpect(jsonPath("$.historial[0].status").value("INGRESADA"))
			.andExpect(jsonPath("$.historial[0].por").value("Diego Tatin"));
	}

	@Test
	void rechazaRutInvalido() throws Exception {
		enviar(PACIENTE, datos("11.111.111-2"), new MockMultipartFile("archivo", "r.jpg", "image/jpeg", JPEG))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("RUT")));
	}

	@Test
	void exigeArchivo() throws Exception {
		enviar(PACIENTE, datos("11.111.111-1"), null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Adjunta la receta médica para poder validarla."));
	}

	@Test
	void rechazaArchivoDisfrazadoDeImagen() throws Exception {
		byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
		enviar(PACIENTE, datos("11.111.111-1"), new MockMultipartFile("archivo", "foto.png", "image/png", html))
			.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void exigeDireccionSiEsDespacho() throws Exception {
		String json = datos("11.111.111-1").replace("RETIRO_TIENDA", "DESPACHO_DOMICILIO");
		enviar(PACIENTE, json, new MockMultipartFile("archivo", "r.jpg", "image/jpeg", JPEG))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("dirección")));
	}

	@Test
	void cadaPacienteVeSoloSusRecetas() throws Exception {
		long id = crearReceta();

		mvc.perform(get(BASE).with(PACIENTE)).andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(get(BASE).with(OTRO_PACIENTE)).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get(BASE + "/" + id).with(OTRO_PACIENTE)).andExpect(status().isNotFound());
		mvc.perform(get(BASE + "/" + id + "/archivo").with(OTRO_PACIENTE)).andExpect(status().isNotFound());
		mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
	}

	@Test
	void operadorVeTodasYDescargaElArchivo() throws Exception {
		long id = crearReceta();

		mvc.perform(get(BASE).with(OPERADOR)).andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(get(BASE).param("status", "VALIDADA").with(OPERADOR)).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get(BASE + "/" + id + "/archivo").with(OPERADOR))
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/jpeg"))
			.andExpect(content().bytes(JPEG))
			.andExpect(header().string("Content-Disposition", containsString("inline")))
			.andExpect(header().string("Cache-Control", containsString("no-store")));
		// El dueño también puede ver su propio archivo.
		mvc.perform(get(BASE + "/" + id + "/archivo").with(PACIENTE)).andExpect(status().isOk());
	}

	@Test
	void flujoCompletoDeEstados() throws Exception {
		long id = crearReceta();

		cambiarEstado(PACIENTE, id, "VALIDADA", null).andExpect(status().isForbidden());
		cambiarEstado(OPERADOR, id, "EN_PREPARACION", null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("no puede pasar")));

		cambiarEstado(OPERADOR, id, "VALIDADA", "Receta vigente").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("VALIDADA"))
			.andExpect(jsonPath("$.historial", hasSize(2)))
			.andExpect(jsonPath("$.historial[1].nota").value("Receta vigente"))
			.andExpect(jsonPath("$.historial[1].por").value("Q.F. Carla Muñoz"));
		cambiarEstado(OPERADOR, id, "EN_PREPARACION", null).andExpect(status().isOk());
		cambiarEstado(OPERADOR, id, "RECHAZADA", "Ya se está preparando").andExpect(status().isConflict());
		cambiarEstado(OPERADOR, id, "LISTA_RETIRO", null).andExpect(status().isOk());
		cambiarEstado(OPERADOR, id, "DISPENSADA", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.historial", hasSize(5)));

		// El paciente ve el historial completo con los mensajes.
		mvc.perform(get(BASE + "/" + id).with(PACIENTE))
			.andExpect(jsonPath("$.status").value("DISPENSADA"))
			.andExpect(jsonPath("$.historial[1].nota").value("Receta vigente"));
	}

	@Test
	void rechazarExigeMotivo() throws Exception {
		long id = crearReceta();

		cambiarEstado(OPERADOR, id, "RECHAZADA", "  ").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("motivo del rechazo")));
		cambiarEstado(OPERADOR, id, "RECHAZADA", "La foto está borrosa").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RECHAZADA"))
			.andExpect(jsonPath("$.historial[1].nota").value("La foto está borrosa"));
	}

	// ---- Tokens de Entra ID firmados de verdad ----

	@Test
	void operadorConJwtRealDeEntraPuedeValidar() throws Exception {
		long id = crearReceta();
		String token = EntraDePrueba.token(java.util.Map.of("roles", List.of("Operador"), "name", "Q.F. Carla Muñoz"));
		mvc.perform(put(BASE + "/" + id + "/status").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VALIDADA\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.historial[1].por").value("Q.F. Carla Muñoz"));
	}

	@Test
	void pacienteConJwtRealNoPuedeCambiarEstados() throws Exception {
		long id = crearReceta();
		String token = EntraDePrueba.token(java.util.Map.of());
		mvc.perform(put(BASE + "/" + id + "/status").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VALIDADA\"}"))
			.andExpect(status().isForbidden());
	}
}
