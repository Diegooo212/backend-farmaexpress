package cl.farmaexpress.notifications.eventos;

import java.time.Instant;

/** Evento {@code pedido.estado-cambiado} que publica el BFF cuando el personal avanza un pedido. */
public record PedidoEstadoCambiado(String eventoId, Long pedidoId, String cuentaId, String estado, String entrega,
		String sucursal, String nota, Instant fecha) {
}
