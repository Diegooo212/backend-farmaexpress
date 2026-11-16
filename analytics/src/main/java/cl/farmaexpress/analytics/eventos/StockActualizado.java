package cl.farmaexpress.analytics.eventos;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Evento del tópico compactado {@code farmaexpress.stock} (clave = id del producto). Lo publica inventory
 * cada vez que cambia el stock de un producto. Como el tópico es compactado, Kafka conserva el último valor
 * de cada producto: un consumidor nuevo reconstruye el stock vigente leyendo el tópico desde el inicio.
 *
 * @param stockPorPedido unidades que se pueden comprar en un solo pedido (la sucursal con más stock)
 * @param stockTotal unidades vendibles sumando todas las sucursales
 */
public record StockActualizado(
		@NotBlank String eventoId,
		@NotNull @Min(1) @Max(1) Integer version,
		@NotNull Long medicamentoId,
		@NotNull @PositiveOrZero Integer stockPorPedido,
		@NotNull @PositiveOrZero Integer stockTotal,
		@NotNull Instant fecha) {
}
