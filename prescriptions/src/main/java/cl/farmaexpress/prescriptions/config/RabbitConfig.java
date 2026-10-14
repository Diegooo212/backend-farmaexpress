package cl.farmaexpress.prescriptions.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mensajería de recetas en un solo lugar. Este servicio solo PUBLICA receta.estado-cambiado (lo
 * consume notifications); las colas las declaran los consumidores.
 *
 * Mensajería asíncrona con RabbitMQ. Todos los microservicios publican en el mismo exchange
 * de tipo topic; cada consumidor decide qué routing keys le interesan (receta.*, pedido.*).
 */
@Configuration
public class RabbitConfig {

	public static final String EXCHANGE = "farmaexpress.eventos";

	public static final String RK_RECETA_ESTADO_CAMBIADO = "receta.estado-cambiado";

	@Bean
	TopicExchange eventosExchange() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	/** Los eventos viajan como JSON (legibles en la consola de RabbitMQ). */
	@Bean
	MessageConverter mensajesJson() {
		return new JacksonJsonMessageConverter();
	}
}
