package cl.farmaexpress.payments.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mensajería de pagos en un solo lugar. Este servicio solo PUBLICA (es el productor de la saga de
 * cobro); las colas las declaran los consumidores (bff, inventory, notifications).
 *
 * <pre>
 * payments ──pago.aprobado──►  farmaexpress.eventos (topic) ──► bff, inventory, notifications
 *          ──pago.rechazado─►                               ──► bff, notifications
 * </pre>
 */
@Configuration
public class RabbitConfig {

	public static final String EXCHANGE = "farmaexpress.eventos";

	public static final String RK_PAGO_APROBADO = "pago.aprobado";
	public static final String RK_PAGO_RECHAZADO = "pago.rechazado";

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
