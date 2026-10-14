package cl.farmaexpress.inventory.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Toda la mensajería del inventario en un solo lugar (nombres de exchanges, colas, DLQ y routing keys).
 *
 * <pre>
 * Consume: farmaexpress.eventos (topic) ──pago.aprobado──► inventory.pagos ─(falla)─► farmaexpress.eventos.dlx ─► inventory.pagos.dlq
 * Publica: inventario.stock-cambiado (lo consume catalog) y reserva.expirada (lo consume el BFF).
 * </pre>
 */
@Configuration
public class RabbitConfig {

	public static final String EXCHANGE = "farmaexpress.eventos";
	public static final String DLX = "farmaexpress.eventos.dlx";

	public static final String RK_PAGO_APROBADO = "pago.aprobado";
	public static final String RK_STOCK_CAMBIADO = "inventario.stock-cambiado";
	public static final String RK_RESERVA_EXPIRADA = "reserva.expirada";

	public static final String COLA_PAGOS = "inventory.pagos";
	public static final String DLQ_PAGOS = COLA_PAGOS + ".dlq";

	@Bean
	TopicExchange eventosExchange() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	@Bean
	DirectExchange eventosDlx() {
		return new DirectExchange(DLX, true, false);
	}

	@Bean
	Queue colaPagos() {
		return QueueBuilder.durable(COLA_PAGOS).deadLetterExchange(DLX).deadLetterRoutingKey(DLQ_PAGOS).build();
	}

	@Bean
	Queue colaPagosFallidos() {
		return QueueBuilder.durable(DLQ_PAGOS).build();
	}

	@Bean
	Binding pagosBinding() {
		return BindingBuilder.bind(colaPagos()).to(eventosExchange()).with(RK_PAGO_APROBADO);
	}

	@Bean
	Binding pagosFallidosBinding() {
		return BindingBuilder.bind(colaPagosFallidos()).to(eventosDlx()).with(DLQ_PAGOS);
	}

	/** Los eventos viajan como JSON (legibles en la consola de RabbitMQ). */
	@Bean
	MessageConverter mensajesJson() {
		return new JacksonJsonMessageConverter();
	}
}
