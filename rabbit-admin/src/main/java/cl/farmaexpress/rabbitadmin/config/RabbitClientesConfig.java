package cl.farmaexpress.rabbitadmin.config;

import java.time.Duration;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Los dos "clientes" con que se habla con RabbitMQ. El resto del código (servicio y controlador)
 * no conoce estos detalles: solo recibe los beans ya configurados.
 * <ul>
 * <li>{@link AmqpAdmin} (protocolo AMQP): declara y elimina colas, exchanges y bindings;</li>
 * <li>{@code managementRestClient} (API HTTP del plugin management): lista lo que existe.</li>
 * </ul>
 */
@Configuration
public class RabbitClientesConfig {

	@Bean
	AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
		RabbitAdmin admin = new RabbitAdmin(connectionFactory);
		// Si una operación falla (por ejemplo, la cola ya existe con otros argumentos) se informa el error.
		admin.setIgnoreDeclarationExceptions(false);
		return admin;
	}

	@Bean
	@Qualifier("management")
	RestClient managementRestClient(RabbitAdminProperties props,
			@Value("${spring.rabbitmq.username}") String usuario, @Value("${spring.rabbitmq.password}") String clave) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(8));
		return RestClient.builder()
			.baseUrl(props.managementUrl())
			.defaultHeaders(h -> h.setBasicAuth(usuario, clave))
			.requestFactory(factory)
			.build();
	}
}
