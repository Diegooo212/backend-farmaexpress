package cl.farmaexpress.notifications.eventos;

import java.time.Instant;

/** Evento {@code pago.aprobado} que publica el microservicio de pagos. */
public record PagoAprobado(String eventoId, Long pagoId, Long pedidoId, Long reservaId, String cuentaId, Integer monto,
		String metodo, String referencia, Instant fecha) {
}
