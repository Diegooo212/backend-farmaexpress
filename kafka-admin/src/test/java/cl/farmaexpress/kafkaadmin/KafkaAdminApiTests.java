package cl.farmaexpress.kafkaadmin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * API del administrador de Kafka contra un broker Kafka real embebido (las reglas de 3 réplicas se
 * prueban contra el clúster de docker-compose.kafka.yml).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EntraDePrueba.class)
@EmbeddedKafka(count = 1, topics = "farmaexpress.ventas", partitions = 3)
class KafkaAdminApiTests {

	private static final String BASE = "/api/admin/kafka";

	@Autowired
	MockMvc mvc;

	@Autowired
	EmbeddedKafkaBroker kafka;

	private static RequestPostProcessor con(String... permisos) {
		return jwt().authorities(java.util.Arrays.stream(permisos).map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new));
	}

	private static final RequestPostProcessor ADMIN = con("SCOPE_access_as_user", "ROLE_Admin");
	private static final RequestPostProcessor OPERADOR = con("SCOPE_access_as_user", "ROLE_Operador");

	@Test
	void soloElAdministradorPuedeAdministrar() throws Exception {
		mvc.perform(get(BASE + "/topics")).andExpect(status().isUnauthorized());
		mvc.perform(get(BASE + "/topics").with(OPERADOR)).andExpect(status().isForbidden());
		mvc.perform(get(BASE + "/topics").with(ADMIN)).andExpect(status().isOk());
	}

	@Test
	void muestraLosBrokersDelCluster() throws Exception {
		mvc.perform(get(BASE + "/cluster").with(ADMIN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.brokers", hasSize(1)))
			.andExpect(jsonPath("$.brokers[0].controlador").value(true));
	}

	@Test
	void creaUnTopicoConParticionesReplicasYConfiguracion() throws Exception {
		mvc.perform(post(BASE + "/topics").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("""
				{"nombre":"pruebas.creado","particiones":3,"replicas":1,
				 "config":{"cleanup.policy":"compact","retention.ms":"86400000"}}"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.particiones", hasSize(3)))
			.andExpect(jsonPath("$.replicas").value(1))
			.andExpect(jsonPath("$.particiones[0].isr", hasSize(1)))
			.andExpect(jsonPath("$.config[?(@.nombre=='cleanup.policy')].valor", hasItem("compact")))
			.andExpect(jsonPath("$.config[?(@.nombre=='cleanup.policy')].personalizada", hasItem(true)));

		mvc.perform(post(BASE + "/topics").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nombre\":\"pruebas.creado\",\"particiones\":1,\"replicas\":1}"))
			.andExpect(status().isConflict());
	}

	@Test
	void rechazaDatosYConfiguracionesInvalidas() throws Exception {
		mvc.perform(post(BASE + "/topics").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nombre\":\"con espacios\",\"particiones\":3,\"replicas\":3}"))
			.andExpect(status().isBadRequest());
		mvc.perform(post(BASE + "/topics").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nombre\":\"pruebas.replicas\",\"particiones\":3,\"replicas\":4}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("3 brokers")));
		// Más réplicas que brokers disponibles (aquí hay 1).
		mvc.perform(post(BASE + "/topics").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nombre\":\"pruebas.replicas\",\"particiones\":3,\"replicas\":3}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("brokers disponibles (1)")));
		mvc.perform(post(BASE + "/topics").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nombre\":\"pruebas.config\",\"particiones\":1,\"replicas\":1,\"config\":{\"cleanup.policy\":\"borrar\"}}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("cleanup.policy")));
		mvc.perform(post(BASE + "/topics").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nombre\":\"pruebas.config\",\"particiones\":1,\"replicas\":1,\"config\":{\"retention.horas\":\"5\"}}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("no se puede administrar")));
		mvc.perform(post(BASE + "/topics").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nombre\":\"pruebas.isr\",\"particiones\":1,\"replicas\":1,\"config\":{\"min.insync.replicas\":\"2\"}}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("min.insync.replicas")));
	}

	@Test
	void modificaConfiguracionesYAumentaParticiones() throws Exception {
		mvc.perform(post(BASE + "/topics").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nombre\":\"pruebas.modificar\",\"particiones\":2,\"replicas\":1}"))
			.andExpect(status().isCreated());

		mvc.perform(put(BASE + "/topics/pruebas.modificar/config").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"config\":{\"retention.ms\":\"3600000\"}}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.config[?(@.nombre=='retention.ms')].valor", hasItem("3600000")));
		// Vacío: vuelve al valor por defecto del broker.
		mvc.perform(put(BASE + "/topics/pruebas.modificar/config").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"config\":{\"retention.ms\":\"\"}}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.config[?(@.nombre=='retention.ms')].personalizada", hasItem(false)));

		mvc.perform(post(BASE + "/topics/pruebas.modificar/partitions").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"total\":4}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.particiones", hasSize(4)));
		mvc.perform(post(BASE + "/topics/pruebas.modificar/partitions").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"total\":3}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", containsString("solo permite aumentarlas")));
	}

	@Test
	void protegeLosTopicosDelSistema() throws Exception {
		mvc.perform(delete(BASE + "/topics/farmaexpress.ventas").with(ADMIN)).andExpect(status().isConflict());
		mvc.perform(post(BASE + "/topics/farmaexpress.ventas/partitions").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"total\":6}"))
			.andExpect(status().isConflict());
		mvc.perform(delete(BASE + "/topics/no.existe").with(ADMIN)).andExpect(status().isNotFound());
	}

	/** En Windows el broker no puede renombrar la carpeta del tópico eliminado; en Linux (Docker) sí. */
	@Test
	@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Kafka en Windows falla al borrar la carpeta de un tópico")
	void eliminaUnTopicoQueNoEsDelSistema() throws Exception {
		mvc.perform(post(BASE + "/topics").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nombre\":\"pruebas.eliminar\",\"particiones\":1,\"replicas\":1}"))
			.andExpect(status().isCreated());
		mvc.perform(delete(BASE + "/topics/pruebas.eliminar").with(ADMIN)).andExpect(status().isNoContent());
		mvc.perform(get(BASE + "/topics/pruebas.eliminar").with(ADMIN)).andExpect(status().isNotFound());
	}

	@Test
	void muestraElLagDeUnGrupoYReiniciaSusOffsets() throws Exception {
		String topico = "pruebas.lag";
		mvc.perform(post(BASE + "/topics").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nombre\":\"" + topico + "\",\"particiones\":1,\"replicas\":1}"))
			.andExpect(status().isCreated());

		Map<String, Object> productor = Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString(),
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		try (KafkaProducer<String, String> p = new KafkaProducer<>(productor)) {
			for (int i = 0; i < 5; i++) {
				p.send(new ProducerRecord<>(topico, "k" + i, "mensaje " + i)).get();
			}
		}
		// Un consumidor del grupo lee y confirma solo 2 mensajes: quedan 3 pendientes (lag = 3).
		Map<String, Object> consumidor = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString(),
				ConsumerConfig.GROUP_ID_CONFIG, "grupo-pruebas",
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		try (KafkaConsumer<String, String> c = new KafkaConsumer<>(consumidor)) {
			c.subscribe(List.of(topico));
			long limite = System.currentTimeMillis() + 15_000;
			while (c.assignment().isEmpty() && System.currentTimeMillis() < limite) {
				c.poll(Duration.ofMillis(200));
			}
			org.apache.kafka.common.TopicPartition tp = new org.apache.kafka.common.TopicPartition(topico, 0);
			c.commitSync(Map.of(tp, new org.apache.kafka.clients.consumer.OffsetAndMetadata(2)));
		}

		mvc.perform(get(BASE + "/consumer-groups/grupo-pruebas").with(ADMIN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.offsets[0].offsetConsumido").value(2))
			.andExpect(jsonPath("$.offsets[0].offsetFinal").value(5))
			.andExpect(jsonPath("$.lagTotal").value(3));
		mvc.perform(get(BASE + "/consumer-groups").with(ADMIN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.grupo=='grupo-pruebas')].lagTotal", hasItem(3)));

		mvc.perform(post(BASE + "/consumer-groups/grupo-pruebas/reset-offsets").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"topico\":\"" + topico + "\",\"posicion\":\"LATEST\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lagTotal").value(0));
		mvc.perform(post(BASE + "/consumer-groups/grupo-pruebas/reset-offsets").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"topico\":\"" + topico + "\",\"posicion\":\"EARLIEST\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lagTotal").value(5));

		mvc.perform(delete(BASE + "/consumer-groups/grupo-pruebas").with(ADMIN)).andExpect(status().isNoContent());
		mvc.perform(get(BASE + "/consumer-groups/grupo-pruebas").with(ADMIN)).andExpect(status().isNotFound());
	}
}
