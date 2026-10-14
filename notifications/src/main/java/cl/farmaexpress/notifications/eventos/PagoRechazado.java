package cl.farmaexpress.notifications.eventos;

import java.time.Instant;

/** Evento {@code pago.rechazado} que publica el microservicio de pagos. */
public record PagoRechazado(String eventoId, Long pagoId, Long pedidoId, Long reservaId, String cuentaId, Integer monto,
		String metodo, String motivo, Instant fecha) {
}
