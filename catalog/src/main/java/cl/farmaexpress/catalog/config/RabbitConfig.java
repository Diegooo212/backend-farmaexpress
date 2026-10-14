package cl.farmaexpress.catalog.config;

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
 * Toda la mensajería del catálogo en un solo lugar (nombres de exchanges, colas, DLQ y routing keys).
 *
 * <pre>
 * farmaexpress.eventos (topic) ──inventario.stock-cambiado──► catalog.stock
 *                                                                │ (falla)
 *                     farmaexpress.eventos.dlx (direct) ◄────────┘──► catalog.stock.dlq
 * </pre>
 */
@Configuration
public class RabbitConfig {

	public static final String EXCHANGE = "farmaexpress.eventos";
	public static final String DLX = "farmaexpress.eventos.dlx";

	public static final String RK_STOCK_CAMBIADO = "inventario.stock-cambiado";

	public static final String COLA_STOCK = "catalog.stock";
	public static final String DLQ_STOCK = COLA_STOCK + ".dlq";

	@Bean
	TopicExchange eventosExchange() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	@Bean
	DirectExchange eventosDlx() {
		return new DirectExchange(DLX, true, false);
	}

	@Bean
	Queue colaStock() {
		return QueueBuilder.durable(COLA_STOCK).deadLetterExchange(DLX).deadLetterRoutingKey(DLQ_STOCK).build();
	}

	@Bean
	Queue colaStockFallidos() {
		return QueueBuilder.durable(DLQ_STOCK).build();
	}

	@Bean
	Binding stockBinding() {
		return BindingBuilder.bind(colaStock()).to(eventosExchange()).with(RK_STOCK_CAMBIADO);
	}

	@Bean
	Binding stockFallidosBinding() {
		return BindingBuilder.bind(colaStockFallidos()).to(eventosDlx()).with(DLQ_STOCK);
	}

	/** Los eventos viajan como JSON (legibles en la consola de RabbitMQ). */
	@Bean
	MessageConverter mensajesJson() {
		return new JacksonJsonMessageConverter();
	}
}
