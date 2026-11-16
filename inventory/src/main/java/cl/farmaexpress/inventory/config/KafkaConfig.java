package cl.farmaexpress.inventory.config;

import java.time.Duration;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Streaming con Kafka: inventory publica el stock vigente de cada producto en el tópico compactado
 * {@code farmaexpress.stock} (clave = id del producto). RabbitMQ sigue avisando al catálogo
 * (inventario.stock-cambiado); Kafka conserva el último stock de cada producto para la analítica.
 *
 * <p>Misma configuración que kafka/kafka.sh: si el tópico no existe al arrancar, Spring lo crea.
 */
@Configuration
public class KafkaConfig {

	public static final String TOPICO_STOCK = "farmaexpress.stock";
	public static final String SUFIJO_DLT = ".dlt";
	public static final int PARTICIONES = 3;

	/**
	 * Compactado: Kafka conserva el último mensaje de cada clave (producto) y descarta los anteriores.
	 * min.compaction.lag.ms deja 1 minuto los cambios recientes sin compactar y segment.ms cierra un segmento
	 * por hora para que la compactación actúe. Las lápidas (mensaje vacío) se guardan 1 día.
	 */
	@Bean
	NewTopic topicoStock(@Value("${farmaexpress.kafka.replicas}") int replicas) {
		return TopicBuilder.name(TOPICO_STOCK)
			.partitions(PARTICIONES)
			.replicas(replicas)
			.compact()
			.config(TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG, String.valueOf(Duration.ofMinutes(1).toMillis()))
			.config(TopicConfig.SEGMENT_MS_CONFIG, String.valueOf(Duration.ofHours(1).toMillis()))
			.config(TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG, "0.1")
			.config(TopicConfig.DELETE_RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(1).toMillis()))
			.config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(Math.min(2, replicas)))
			.build();
	}

	/** Eventos de stock que la analítica no pudo procesar: se guardan 30 días para revisarlos. */
	@Bean
	NewTopic topicoStockDlt(@Value("${farmaexpress.kafka.replicas}") int replicas) {
		return TopicBuilder.name(TOPICO_STOCK + SUFIJO_DLT)
			.partitions(PARTICIONES)
			.replicas(replicas)
			.config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
			.config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(30).toMillis()))
			.config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(Math.min(2, replicas)))
			.build();
	}
}
