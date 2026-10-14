package cl.farmaexpress.rabbitadmin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import cl.farmaexpress.rabbitadmin.service.RabbitManagementClient;

/** API del administrador de RabbitMQ: seguridad, validaciones y reglas (RabbitMQ simulado). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EntraDePrueba.class)
class RabbitAdminApiTests {

	private static final String BASE = "/api/admin/rabbit";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	AmqpAdmin amqp;

	@MockitoBean
	RabbitManagementClient management;

	private static RequestPostProcessor con(String... permisos) {
		return jwt().authorities(java.util.Arrays.stream(permisos).map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new));
	}

	private static final RequestPostProcessor ADMIN = con("SCOPE_access_as_user", "ROLE_Admin");
	private static final RequestPostProcessor OPERADOR = con("SCOPE_access_as_user", "ROLE_Operador");

	@Test
	void soloElAdministradorPuedeAdministrar() throws Exception {
		mvc.perform(get(BASE + "/queues")).andExpect(status().isUnauthorized());
		mvc.perform(get(BASE + "/queues").with(OPERADOR)).andExpect(status().isForbidden());
		when(management.colas()).thenReturn(List.of());
		mvc.perform(get(BASE + "/queues").with(ADMIN)).andExpect(status().isOk());
	}

	@Test
	void listaLasColasConSuDlqYMarcaLasDelSistema() throws Exception {
		when(management.colas()).thenReturn(List.of(
				Map.of("name", "notifications.recetas", "type", "classic", "durable", true, "messages", 2, "consumers", 1,
						"arguments", Map.of("x-dead-letter-routing-key", "notifications.dlq")),
				Map.of("name", "tickets.soporte", "type", "quorum", "durable", true, "messages", 0, "consumers", 0,
						"arguments", Map.of())));

		mvc.perform(get(BASE + "/queues").with(ADMIN))
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].dlq").value("notifications.dlq"))
			.andExpect(jsonPath("$[0].protegida").value(true))
			.andExpect(jsonPath("$[0].mensajes").value(2))
			.andExpect(jsonPath("$[1].protegida").value(false));
	}

	@Test
	void creaUnaColaConSuDlqConectadaAlExchangeDeFallidos() throws Exception {
		mvc.perform(post(BASE + "/queues").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"tickets.soporte\",\"tipo\":\"QUORUM\",\"ttlMs\":60000}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.cola.nombre").value("tickets.soporte"))
			.andExpect(jsonPath("$.cola.dlq").value("tickets.soporte.dlq"))
			.andExpect(jsonPath("$.argumentos['x-queue-type']").value("quorum"))
			.andExpect(jsonPath("$.argumentos['x-dead-letter-exchange']").value("farmaexpress.eventos.dlx"))
			.andExpect(jsonPath("$.recursosCreados", hasSize(3)));

		verify(amqp).declareQueue(argThat((Queue q) -> q.getName().equals("tickets.soporte")));
		verify(amqp).declareQueue(argThat((Queue q) -> q.getName().equals("tickets.soporte.dlq")));
		verify(amqp).declareBinding(argThat((Binding b) -> b.getExchange().equals("farmaexpress.eventos.dlx")
				&& b.getDestination().equals("tickets.soporte.dlq")));
	}

	@Test
	void validaLosDatosAntesDeTocarRabbit() throws Exception {
		mvc.perform(post(BASE + "/queues").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.nombre").exists());
		mvc.perform(post(BASE + "/queues").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"cola con espacios\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("sin espacios")));
		mvc.perform(post(BASE + "/queues").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"tickets\",\"ttlMs\":-5}"))
			.andExpect(status().isBadRequest());
		mvc.perform(post(BASE + "/queues").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"tickets\",\"tipo\":\"QUORUM\",\"durable\":false}"))
			.andExpect(status().isBadRequest());
		mvc.perform(post(BASE + "/exchanges").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"tickets.eventos\",\"tipo\":\"SUPER\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(post(BASE + "/exchanges").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"tickets.eventos\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.tipo").exists());

		verify(amqp, never()).declareQueue(any());
		verify(amqp, never()).declareExchange(any());
	}

	@Test
	void noPermiteTocarLosRecursosDelSistemaNiDuplicar() throws Exception {
		mvc.perform(post(BASE + "/queues").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"notifications.nueva\"}"))
			.andExpect(status().isConflict());
		mvc.perform(delete(BASE + "/exchanges/farmaexpress.eventos").with(ADMIN)).andExpect(status().isConflict());

		when(amqp.getQueueInfo("tickets.soporte")).thenReturn(new QueueInformation("tickets.soporte", 0, 0));
		mvc.perform(post(BASE + "/queues").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"tickets.soporte\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("Ya existe")));
	}

	@Test
	void noEliminaUnaColaConMensajesSalvoQueSeFuerce() throws Exception {
		mvc.perform(delete(BASE + "/queues/no.existe").with(ADMIN)).andExpect(status().isNotFound());

		when(amqp.getQueueInfo("tickets.soporte")).thenReturn(new QueueInformation("tickets.soporte", 5, 0));
		mvc.perform(delete(BASE + "/queues/tickets.soporte").with(ADMIN))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("5 mensajes")));
		verify(amqp, never()).deleteQueue(anyString());

		mvc.perform(delete(BASE + "/queues/tickets.soporte?forzar=true").with(ADMIN)).andExpect(status().isNoContent());
		verify(amqp).deleteQueue("tickets.soporte");
	}

	@Test
	void creaYEliminaExchangesDirectYTopic() throws Exception {
		when(management.exchange("tickets.directo")).thenReturn(Optional.empty());
		mvc.perform(post(BASE + "/exchanges").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"tickets.directo\",\"tipo\":\"DIRECT\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tipo").value("direct"));
		verify(amqp).declareExchange(argThat((Exchange e) -> e.getName().equals("tickets.directo") && e.getType().equals("direct")));

		mvc.perform(delete(BASE + "/exchanges/tickets.directo").with(ADMIN)).andExpect(status().isNotFound());
		when(management.exchange("tickets.topic")).thenReturn(Optional.of(Map.of("name", "tickets.topic", "type", "topic")));
		mvc.perform(delete(BASE + "/exchanges/tickets.topic").with(ADMIN)).andExpect(status().isNoContent());
		verify(amqp).deleteExchange("tickets.topic");
	}

	@Test
	void conectaYDesconectaColasValidandoLaRoutingKeySegunElTipo() throws Exception {
		when(management.exchange("tickets.directo")).thenReturn(Optional.of(Map.of("name", "tickets.directo", "type", "direct")));
		when(amqp.getQueueInfo("tickets.soporte")).thenReturn(new QueueInformation("tickets.soporte", 0, 0));
		when(management.bindings()).thenReturn(List.of());

		mvc.perform(post(BASE + "/bindings").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
				.content("{\"exchange\":\"tickets.directo\",\"cola\":\"tickets.soporte\",\"routingKey\":\"ticket.*\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("topic")));
		mvc.perform(post(BASE + "/bindings").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
				.content("{\"exchange\":\"tickets.directo\",\"cola\":\"tickets.soporte\",\"routingKey\":\"ticket.creado\"}"))
			.andExpect(status().isCreated());
		verify(amqp).declareBinding(argThat((Binding b) -> b.getRoutingKey().equals("ticket.creado")));

		when(management.bindings()).thenReturn(List.of(Map.of("source", "tickets.directo", "destination", "tickets.soporte",
				"destination_type", "queue", "routing_key", "ticket.creado")));
		mvc.perform(delete(BASE + "/bindings").with(ADMIN).param("exchange", "tickets.directo")
				.param("cola", "tickets.soporte").param("routingKey", "ticket.creado"))
			.andExpect(status().isNoContent());
		verify(amqp).removeBinding(argThat(b -> b.getRoutingKey().equals("ticket.creado")));

		mvc.perform(get(BASE + "/bindings").with(ADMIN)).andExpect(jsonPath("$[0].cola").value("tickets.soporte"));
		mvc.perform(delete(BASE + "/bindings").with(ADMIN).param("exchange", "tickets.directo")
				.param("cola", "tickets.soporte").param("routingKey", "otra.clave"))
			.andExpect(status().isNotFound());
		verify(amqp, never()).declareBinding(argThat((Binding b) -> b.getRoutingKey().equals("ticket.*")));
		verify(amqp, never()).deleteExchange(eq("tickets.directo"));
	}
}
