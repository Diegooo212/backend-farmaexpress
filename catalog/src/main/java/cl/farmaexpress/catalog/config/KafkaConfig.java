package cl.farmaexpress.catalog.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.kafka.support.serializer.ToStringSerializer;

import cl.farmaexpress.catalog.eventos.EventoInvalidoException;

/**
 * Mensajería Kafka del catálogo: el consumer group {@code catalog-ventas} lee el tópico
 * {@code farmaexpress.ventas} para llevar la cuenta de "más vendidos".
 *
 * <pre>
 * farmaexpress.ventas ──► grupo analytics-ventas (analítica)       cada grupo recibe TODAS las ventas y
 *                     └─► grupo catalog-ventas   (este servicio)   avanza a su propio ritmo (su propio offset)
 * </pre>
 * Mismo manejo de errores que la analítica: datos inválidos van directo al DLT; fallas transitorias se
 * reintentan 3 veces con espera exponencial y luego van al DLT (farmaexpress.ventas.dlt) con un log ERROR.
 */
@Configuration
public class KafkaConfig {

	private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

	public static final String TOPICO_VENTAS = "farmaexpress.ventas";
	/** Dead letter topic: mismo nombre con este sufijo (farmaexpress.ventas.dlt). */
	public static final String SUFIJO_DLT = ".dlt";

	/** Grupo propio: recibe todas las ventas aunque la analítica también las lea. */
	public static final String GRUPO_VENTAS = "catalog-ventas";

	/**
	 * Publica en el DLT conservando la partición original. Un JSON ilegible llega como bytes crudos (se
	 * reenvía tal cual) y un evento inválido como objeto (se reenvía como JSON). Kafka agrega en los headers
	 * el tópico, partición y offset de origen y la excepción.
	 */
	@Bean
	DeadLetterPublishingRecoverer recuperadorDlt(KafkaTemplate<Object, Object> plantillaJsonDlt,
			KafkaTemplate<Object, byte[]> plantillaBytesDlt) {
		Map<Class<?>, KafkaOperations<?, ?>> plantillas = new LinkedHashMap<>();
		plantillas.put(byte[].class, plantillaBytesDlt);
		plantillas.put(Object.class, plantillaJsonDlt);
		return new DeadLetterPublishingRecoverer(plantillas,
				(registro, error) -> new TopicPartition(registro.topic() + SUFIJO_DLT, registro.partition()));
	}

	/**
	 * Manejo de errores de todos los @KafkaListener (Spring Boot lo asocia a los contenedores):
	 * reintentos con espera exponencial (0,5 s, 1 s, 2 s) solo para errores transitorios; luego, o de
	 * inmediato si el dato es inválido, el mensaje va al DLT y el consumidor sigue con el siguiente. Un
	 * mensaje defectuoso nunca detiene la partición.
	 */
	@Bean
	DefaultErrorHandler manejadorErrores(DeadLetterPublishingRecoverer recuperadorDlt) {
		ExponentialBackOffWithMaxRetries espera = new ExponentialBackOffWithMaxRetries(3);
		espera.setInitialInterval(500);
		espera.setMultiplier(2);
		espera.setMaxInterval(4_000);
		DefaultErrorHandler manejador = new DefaultErrorHandler((registro, error) -> {
			log.error("Evento enviado a {}{} desde {}-{}@{} (clave {}): {}", registro.topic(), SUFIJO_DLT, registro.topic(),
					registro.partition(), registro.offset(), registro.key(), causa(error));
			recuperadorDlt.accept(registro, error);
		}, espera);
		// Datos inválidos: reintentar no los corrige. (JSON ilegible ya está en la lista por defecto.)
		manejador.addNotRetryableExceptions(EventoInvalidoException.class);
		manejador.setRetryListeners((registro, error, intento) -> log.warn("Intento {} fallido en {}-{}@{}: {}", intento,
				registro.topic(), registro.partition(), registro.offset(), causa(error)));
		return manejador;
	}

	@Bean
	DefaultKafkaProducerFactory<Object, Object> productorJsonDlt(@Value("${spring.kafka.bootstrap-servers}") String brokers) {
		return new DefaultKafkaProducerFactory<>(configDlt(brokers), new ToStringSerializer<>(), new JacksonJsonSerializer<>());
	}

	@Bean
	DefaultKafkaProducerFactory<Object, byte[]> productorBytesDlt(@Value("${spring.kafka.bootstrap-servers}") String brokers) {
		return new DefaultKafkaProducerFactory<>(configDlt(brokers), new ToStringSerializer<>(), new ByteArraySerializer());
	}

	@Bean
	KafkaTemplate<Object, Object> plantillaJsonDlt(DefaultKafkaProducerFactory<Object, Object> productorJsonDlt) {
		return new KafkaTemplate<>(productorJsonDlt);
	}

	@Bean
	KafkaTemplate<Object, byte[]> plantillaBytesDlt(DefaultKafkaProducerFactory<Object, byte[]> productorBytesDlt) {
		return new KafkaTemplate<>(productorBytesDlt);
	}

	/** El DLT también es crítico: acks=all e idempotencia para no perder ni duplicar un evento fallido. */
	private static Map<String, Object> configDlt(String brokers) {
		return Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers,
				ProducerConfig.CLIENT_ID_CONFIG, "farmaexpress-catalog-dlt",
				ProducerConfig.ACKS_CONFIG, "all",
				ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
				ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000);
	}

	private static String causa(Throwable error) {
		Throwable t = error;
		while (t.getCause() != null && t.getCause() != t) {
			t = t.getCause();
		}
		return t.getClass().getSimpleName() + ": " + t.getMessage();
	}
}
