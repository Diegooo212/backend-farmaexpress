package cl.farmaexpress.inventory.eventos;

import java.time.Instant;
import java.util.List;

import cl.farmaexpress.inventory.config.RabbitConfig;

/** Mensajes que el inventario publica y consume en RabbitMQ (exchange {@code farmaexpress.eventos}). */
public final class Eventos {

	private Eventos() {
	}

	/**
	 * Publicado: unidades de cada producto que se pueden comprar en un solo pedido (la sucursal con más
	 * stock vendible), para que el catálogo y el carrito no ofrezcan más de lo que se puede entregar.
	 */
	public record StockCambiado(String eventoId, List<StockProducto> items, Instant fecha) {

		public static final String ROUTING_KEY = RabbitConfig.RK_STOCK_CAMBIADO;
	}

	/**
	 * @param stock unidades que se pueden comprar en un solo pedido (la sucursal con más stock)
	 * @param stockTotal unidades vendibles sumando todas las sucursales
	 */
	public record StockProducto(Long medicamentoId, int stock, int stockTotal) {
	}

	/** Publicado: la reserva no se pagó a tiempo y las unidades volvieron a estar disponibles. */
	public record ReservaExpirada(String eventoId, Long reservaId, String referencia, Instant fecha) {

		public static final String ROUTING_KEY = RabbitConfig.RK_RESERVA_EXPIRADA;
	}

	/** Consumido: el microservicio de pagos aprobó el pago de un pedido. */
	public record PagoAprobado(String eventoId, Long pagoId, Long pedidoId, Long reservaId, String cuentaId,
			Integer monto, String metodo, Instant fecha) {
	}
}
