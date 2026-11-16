package cl.farmaexpress.kafkaadmin.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param protegidos prefijos de los tópicos del sistema: no se pueden eliminar ni cambiar sus particiones
 * @param timeout tiempo máximo de espera de cada operación contra el clúster
 */
@ConfigurationProperties(prefix = "farmaexpress.kafka")
public record KafkaAdminProperties(List<String> protegidos, Duration timeout) {

	public boolean esProtegido(String topico) {
		return protegidos.stream().anyMatch(topico::startsWith);
	}

	/** Tópicos internos de Kafka (offsets de los grupos, transacciones): ni siquiera se configuran. */
	public static boolean esInterno(String topico) {
		return topico.startsWith("__");
	}
}
