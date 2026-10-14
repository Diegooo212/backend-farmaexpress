package cl.farmaexpress.prescriptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import cl.farmaexpress.prescriptions.config.RabbitConfig;
import cl.farmaexpress.prescriptions.eventos.RecetaEstadoCambiado;

/**
 * El cambio de estado de una receta publica un evento en RabbitMQ para el microservicio de
 * notificaciones. Sin @Transactional: el evento sale solo después del commit.
 * Usa su propia base H2 para no mezclar datos con RecetaApiTests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@TestPropertySource(properties = { "farmaexpress.eventos.habilitado=true",
		"spring.datasource.url=jdbc:h2:mem:prescriptions-eventos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" })
class EventosRecetaTests {

	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1 };

	@Autowired
	MockMvc mvc;

	@MockitoBean
	RabbitTemplate rabbit;

	private static RequestPostProcessor usuario(String oid, String nombre, String rol) {
		return jwt().jwt(j -> j.subject("sub-" + oid).claim("oid", oid).claim("email", oid + "@test.cl")
				.claim("name", nombre).claim("scp", "access_as_user").claim("roles", List.of(rol)))
			.authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"), new SimpleGrantedAuthority("ROLE_" + rol));
	}

	private static final RequestPostProcessor PACIENTE = usuario("oid-paciente-eventos", "Paciente Eventos", "Cliente");
	private static final RequestPostProcessor OPERADOR = usuario("oid-operador-eventos", "Q.F. Operador", "Operador");

	private long crearReceta() throws Exception {
		String datos = """
				{"pacienteNombre":"Paciente Eventos","rut":"11.111.111-1","email":"p@test.cl","telefono":"+56 9 1111 2222",
				 "metodoDespacho":"RETIRO_TIENDA","farmacia":"Farmacia Centro"}""";
		String json = mvc.perform(multipart("/api/prescriptions")
				.file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE, datos.getBytes(StandardCharsets.UTF_8)))
				.file(new MockMultipartFile("archivo", "receta.jpg", "image/jpeg", JPEG))
				.with(PACIENTE))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	private void cambiarEstado(long id, String body, int esperado) throws Exception {
		mvc.perform(put("/api/prescriptions/" + id + "/status").with(OPERADOR)
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().is(esperado));
	}

	@Test
	void cambiarEstadoPublicaElEventoParaElPaciente() throws Exception {
		long id = crearReceta();
		// Enviar una receta no avisa nada: el aviso es para cuando la farmacia la revisa.
		verify(rabbit, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));

		cambiarEstado(id, "{\"status\":\"RECHAZADA\",\"nota\":\"La receta está vencida\"}", 200);

		ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
		verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RecetaEstadoCambiado.ROUTING_KEY), evento.capture());
		RecetaEstadoCambiado enviado = (RecetaEstadoCambiado) evento.getValue();
		assertThat(enviado.recetaId()).isEqualTo(id);
		assertThat(enviado.cuentaId()).isEqualTo("oid-paciente-eventos");
		assertThat(enviado.estado()).isEqualTo("RECHAZADA");
		assertThat(enviado.nota()).isEqualTo("La receta está vencida");
		assertThat(enviado.eventoId()).isNotBlank();
	}

	@Test
	void unCambioRechazadoNoPublicaNada() throws Exception {
		long id = crearReceta();
		clearInvocations(rabbit);

		// INGRESADA no puede pasar directo a DISPENSADA: 409 y ningún evento.
		cambiarEstado(id, "{\"status\":\"DISPENSADA\"}", 409);

		verify(rabbit, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
	}

	@Test
	void siRabbitMqNoEstaLaRecetaIgualCambiaDeEstado() throws Exception {
		long id = crearReceta();
		doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
			.when(rabbit).convertAndSend(any(String.class), any(String.class), any(Object.class));

		cambiarEstado(id, "{\"status\":\"VALIDADA\"}", 200);
	}
}
