package cl.farmaexpress.kafkaadmin.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Cliente de administración de Kafka (AdminClient) creado con la configuración de spring.kafka.*
 * (bootstrap servers y spring.kafka.admin.properties). Es thread-safe: se comparte en todo el servicio.
 */
@Configuration
public class KafkaClienteConfig {

	@Bean(destroyMethod = "close")
	AdminClient adminClient(KafkaAdmin kafkaAdmin) {
		return AdminClient.create(kafkaAdmin.getConfigurationProperties());
	}
}
