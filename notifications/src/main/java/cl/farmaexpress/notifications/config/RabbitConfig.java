package cl.farmaexpress.notifications.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Toda la mensajería de notificaciones en un solo lugar (nombres de exchanges, colas, DLQ y routing keys).
 * Cada cola tiene su propia DLQ ("&lt;cola&gt;.dlq"), así se sabe de qué flujo vino cada mensaje fallido.
 *
 * <pre>
 * farmaexpress.eventos (topic)                                       farmaexpress.eventos.dlx (direct)
 *   receta.estado-cambiado ──► notifications.recetas          ─(falla)─► notifications.recetas.dlq
 *   pago.aprobado          ──► notifications.pagos-aprobados  ─(falla)─► notifications.pagos-aprobados.dlq
 *   pago.rechazado         ──► notifications.pagos-rechazados ─(falla)─► notifications.pagos-rechazados.dlq
 *   pedido.estado-cambiado ──► notifications.pedidos          ─(falla)─► notifications.pedidos.dlq
 * </pre>
 * Las colas son durables: si este servicio está apagado, los eventos esperan en RabbitMQ y no se pierden.
 */
@Configuration
public class RabbitConfig {

	public static final String EXCHANGE = "farmaexpress.eventos";
	public static final String DLX = "farmaexpress.eventos.dlx";

	public static final String RK_RECETA_ESTADO_CAMBIADO = "receta.estado-cambiado";
	public static final String RK_PAGO_APROBADO = "pago.aprobado";
	public static final String RK_PAGO_RECHAZADO = "pago.rechazado";
	public static final String RK_PEDIDO_ESTADO_CAMBIADO = "pedido.estado-cambiado";

	public static final String COLA_RECETAS = "notifications.recetas";
	public static final String COLA_PAGOS_APROBADOS = "notifications.pagos-aprobados";
	public static final String COLA_PAGOS_RECHAZADOS = "notifications.pagos-rechazados";
	public static final String COLA_PEDIDOS = "notifications.pedidos";
	public static final String SUFIJO_DLQ = ".dlq";

	@Bean
	TopicExchange eventosExchange() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	@Bean
	DirectExchange eventosDlx() {
		return new DirectExchange(DLX, true, false);
	}

	@Bean
	Declarables recetas(TopicExchange eventosExchange, DirectExchange eventosDlx) {
		return colaConDlq(COLA_RECETAS, RK_RECETA_ESTADO_CAMBIADO, eventosExchange, eventosDlx);
	}

	@Bean
	Declarables pagosAprobados(TopicExchange eventosExchange, DirectExchange eventosDlx) {
		return colaConDlq(COLA_PAGOS_APROBADOS, RK_PAGO_APROBADO, eventosExchange, eventosDlx);
	}

	@Bean
	Declarables pagosRechazados(TopicExchange eventosExchange, DirectExchange eventosDlx) {
		return colaConDlq(COLA_PAGOS_RECHAZADOS, RK_PAGO_RECHAZADO, eventosExchange, eventosDlx);
	}

	@Bean
	Declarables pedidos(TopicExchange eventosExchange, DirectExchange eventosDlx) {
		return colaConDlq(COLA_PEDIDOS, RK_PEDIDO_ESTADO_CAMBIADO, eventosExchange, eventosDlx);
	}

	/** El tipo del mensaje se deduce del parámetro del listener (no depende de las clases del productor). */
	@Bean
	MessageConverter mensajesJson() {
		return new JacksonJsonMessageConverter();
	}

	/** Cola + su DLQ + los dos bindings (evento → cola, DLX → DLQ). */
	static Declarables colaConDlq(String nombre, String routingKey, TopicExchange exchange, DirectExchange dlx) {
		String dlq = nombre + SUFIJO_DLQ;
		Queue cola = QueueBuilder.durable(nombre).deadLetterExchange(DLX).deadLetterRoutingKey(dlq).build();
		Queue fallidos = QueueBuilder.durable(dlq).build();
		Binding evento = BindingBuilder.bind(cola).to(exchange).with(routingKey);
		Binding aDlq = BindingBuilder.bind(fallidos).to(dlx).with(dlq);
		return new Declarables(cola, fallidos, evento, aDlq);
	}
}
