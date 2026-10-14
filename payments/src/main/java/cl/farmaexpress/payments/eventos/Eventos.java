package cl.farmaexpress.payments.eventos;

import java.time.Instant;

import cl.farmaexpress.payments.config.RabbitConfig;

/**
 * Eventos que publica el microservicio de pagos (exchange {@code farmaexpress.eventos}):
 * <ul>
 * <li>{@code pago.aprobado}: el BFF marca el pedido como pagado, inventory confirma la salida de los
 * lotes y notifications avisa al cliente;</li>
 * <li>{@code pago.rechazado}: el BFF marca el pedido para reintentar con otro medio y notifications avisa.</li>
 * </ul>
 */
public final class Eventos {

	private Eventos() {
	}

	public record PagoAprobado(String eventoId, Long pagoId, Long pedidoId, Long reservaId, String cuentaId,
			Integer monto, String metodo, String referencia, Instant fecha) {

		public static final String ROUTING_KEY = RabbitConfig.RK_PAGO_APROBADO;
	}

	public record PagoRechazado(String eventoId, Long pagoId, Long pedidoId, Long reservaId, String cuentaId,
			Integer monto, String metodo, String motivo, Instant fecha) {

		public static final String ROUTING_KEY = RabbitConfig.RK_PAGO_RECHAZADO;
	}
}
