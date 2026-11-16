package cl.farmaexpress.inventory.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado en Kafka ({@code farmaexpress.stock}, clave = id del producto): stock vigente de un
 * producto. Uno por producto (y no uno por movimiento con varios productos) para que la compactación del
 * tópico conserve exactamente el último valor de cada producto.
 *
 * @param version versión del esquema del evento
 * @param stockPorPedido unidades que se pueden comprar en un solo pedido (la sucursal con más stock)
 * @param stockTotal unidades vendibles sumando todas las sucursales
 */
public record StockActualizado(String eventoId, int version, Long medicamentoId, int stockPorPedido, int stockTotal,
		Instant fecha) {

	public static final int VERSION = 1;

	public static StockActualizado de(Eventos.StockProducto stock, Instant fecha) {
		return new StockActualizado(UUID.randomUUID().toString(), VERSION, stock.medicamentoId(), stock.stock(),
				stock.stockTotal(), fecha);
	}
}
