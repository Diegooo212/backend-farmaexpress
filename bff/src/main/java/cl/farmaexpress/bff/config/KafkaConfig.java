package cl.farmaexpress.bff.config;

import java.time.Duration;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Streaming con Kafka: el BFF publica cada venta confirmada en el tópico {@code farmaexpress.ventas}
 * (clave = id del pedido). RabbitMQ sigue coordinando el flujo del pedido; Kafka guarda el historial de
 * ventas para que la analítica (y cualquier consumidor futuro) lo procese y lo pueda reprocesar.
 *
 * <p>Los tópicos se declaran aquí con la misma configuración que kafka/kafka.sh: si no existen al arrancar
 * (por ejemplo, un clúster recién creado sin kafka-init), Spring los crea. Si ya existen, no los modifica.
 */
@Configuration
public class KafkaConfig {

	public static final String TOPICO_VENTAS = "farmaexpress.ventas";
	public static final String SUFIJO_DLT = ".dlt";
	/** Una partición por consumidor del grupo analytics-ventas: 3 ventas se procesan en paralelo. */
	public static final int PARTICIONES = 3;

	/**
	 * Historial de ventas: se conserva 7 días (o 1 GiB por partición) para poder reprocesarlo.
	 * @param replicas 3 en el clúster (una copia por broker); KAFKA_REPLICAS=1 con un solo broker de desarrollo
	 */
	@Bean
	NewTopic topicoVentas(@Value("${farmaexpress.kafka.replicas}") int replicas) {
		return TopicBuilder.name(TOPICO_VENTAS)
			.partitions(PARTICIONES)
			.replicas(replicas)
			.config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
			.config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(7).toMillis()))
			.config(TopicConfig.RETENTION_BYTES_CONFIG, String.valueOf(1L << 30))
			.config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(Math.min(2, replicas)))
			.build();
	}

	/** Ventas que la analítica no pudo procesar: se guardan 30 días para revisarlas. */
	@Bean
	NewTopic topicoVentasDlt(@Value("${farmaexpress.kafka.replicas}") int replicas) {
		return TopicBuilder.name(TOPICO_VENTAS + SUFIJO_DLT)
			.partitions(PARTICIONES)
			.replicas(replicas)
			.config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
			.config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(30).toMillis()))
			.config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(Math.min(2, replicas)))
			.build();
	}
}
