package cl.farmaexpress.bff.eventos;

import java.time.Instant;

import cl.farmaexpress.bff.config.RabbitConfig;

/**
 * Publicado en RabbitMQ ({@code pedido.estado-cambiado}) cuando el personal avanza un pedido
 * (en preparación, listo para retiro, en camino, entregado). Lo consume notifications.
 *
 * @param cuentaId oid de Entra ID del cliente
 * @param entrega RETIRO o DESPACHO
 */
public record PedidoEstadoCambiado(String eventoId, Long pedidoId, String cuentaId, String estado, String entrega,
		String sucursal, String nota, Instant fecha) {

	public static final String ROUTING_KEY = RabbitConfig.RK_PEDIDO_ESTADO_CAMBIADO;
}
