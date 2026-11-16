package cl.farmaexpress.analytics.eventos;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Evento del tópico {@code farmaexpress.ventas} (clave = id del pedido). Lo publica el BFF cuando el pago de
 * un pedido queda aprobado. Lleva todo lo que la analítica necesita, sin datos personales del cliente.
 *
 * @param eventoId identificador único del evento (trazabilidad)
 * @param version versión del esquema del evento: permite evolucionarlo sin romper a los consumidores
 * @param pedidoId pedido pagado; también es la clave del mensaje y lo que hace idempotente al consumidor
 * @param fecha momento en que se aprobó el pago
 * @param entrega RETIRO en farmacia o DESPACHO a domicilio
 * @param sucursalId farmacia o almacén desde donde se entrega
 * @param costoDespacho pesos cobrados por el despacho (0 en retiro)
 * @param total total pagado = suma de los subtotales de los productos + costo de despacho
 */
public record VentaRegistrada(
		@NotBlank String eventoId,
		@NotNull @Min(1) @Max(1) Integer version,
		@NotNull Long pedidoId,
		@NotNull Instant fecha,
		@NotBlank @Pattern(regexp = "RETIRO|DESPACHO") String entrega,
		Long sucursalId,
		String sucursalNombre,
		@NotBlank String metodoPago,
		@NotNull @PositiveOrZero Integer costoDespacho,
		@NotNull @Positive Integer total,
		@NotEmpty List<@Valid @NotNull Item> items) {

	/** Un producto del pedido. */
	public record Item(@NotNull Long medicamentoId, @NotBlank String nombre, @NotNull @Positive Integer cantidad,
			@NotNull @PositiveOrZero Integer precioUnitario) {

		public int subtotal() {
			return cantidad * precioUnitario;
		}
	}

	public int unidades() {
		return items.stream().mapToInt(Item::cantidad).sum();
	}
}
