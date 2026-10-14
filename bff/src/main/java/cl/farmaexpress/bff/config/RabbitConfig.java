package cl.farmaexpress.bff.config;

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
 * Toda la mensajería del BFF en un solo lugar: nombres de exchanges, colas, DLQ y routing keys.
 * El resto del código usa estas constantes (no hay nombres sueltos).
 *
 * <pre>
 * Consume (exchange topic farmaexpress.eventos):
 *   pago.aprobado    ──► bff.pagos-aprobados   ─(falla)─► farmaexpress.eventos.dlx (direct) ─► bff.pagos-aprobados.dlq
 *   pago.rechazado   ──► bff.pagos-rechazados  ─(falla)─►                                     ─► bff.pagos-rechazados.dlq
 *   reserva.expirada ──► bff.reservas          ─(falla)─►                                     ─► bff.reservas.dlq
 * Publica:
 *   pedido.estado-cambiado (lo consume notifications)
 * </pre>
 */
@Configuration
public class RabbitConfig {

	// Exchanges compartidos por todos los microservicios.
	public static final String EXCHANGE = "farmaexpress.eventos";
	public static final String DLX = "farmaexpress.eventos.dlx";

	// Routing keys (tipo de evento).
	public static final String RK_PAGO_APROBADO = "pago.aprobado";
	public static final String RK_PAGO_RECHAZADO = "pago.rechazado";
	public static final String RK_RESERVA_EXPIRADA = "reserva.expirada";
	public static final String RK_PEDIDO_ESTADO_CAMBIADO = "pedido.estado-cambiado";

	// Colas de este servicio (cada una con su DLQ "<cola>.dlq").
	public static final String COLA_APROBADOS = "bff.pagos-aprobados";
	public static final String COLA_RECHAZADOS = "bff.pagos-rechazados";
	public static final String COLA_RESERVAS = "bff.reservas";
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
	Declarables pagosAprobados(TopicExchange eventosExchange, DirectExchange eventosDlx) {
		return colaConDlq(COLA_APROBADOS, RK_PAGO_APROBADO, eventosExchange, eventosDlx);
	}

	@Bean
	Declarables pagosRechazados(TopicExchange eventosExchange, DirectExchange eventosDlx) {
		return colaConDlq(COLA_RECHAZADOS, RK_PAGO_RECHAZADO, eventosExchange, eventosDlx);
	}

	@Bean
	Declarables reservasExpiradas(TopicExchange eventosExchange, DirectExchange eventosDlx) {
		return colaConDlq(COLA_RESERVAS, RK_RESERVA_EXPIRADA, eventosExchange, eventosDlx);
	}

	/** Los eventos viajan como JSON (legibles en la consola de RabbitMQ). */
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
