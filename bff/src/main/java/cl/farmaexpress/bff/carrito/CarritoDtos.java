package cl.farmaexpress.bff.carrito;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class CarritoDtos {

	private CarritoDtos() {
	}

	public record ItemRequest(
			@NotNull(message = "Falta el id del medicamento.") Long id,
			@NotNull(message = "Falta la cantidad.")
			@Positive(message = "La cantidad debe ser mayor que cero.")
			@Max(value = 999, message = "La cantidad máxima por producto es 999.") Integer cantidad) {
	}

	public record CarritoRequest(
			@NotNull(message = "Faltan los productos del carrito.")
			@Size(max = 100, message = "El carrito admite hasta 100 productos distintos.") List<@Valid ItemRequest> items) {
	}

	/** Mismo formato de ítem que usa el frontend ({ id, nombre, precio, cantidad }) más el stock actual. */
	public record ItemResponse(Long id, String nombre, Integer precio, Integer cantidad, Integer stock) {
	}

	public record CarritoResponse(List<ItemResponse> items, Integer total, Integer count) {

		public static CarritoResponse de(List<ItemResponse> items) {
			int total = items.stream().mapToInt(i -> i.precio() * i.cantidad()).sum();
			int count = items.stream().mapToInt(ItemResponse::cantidad).sum();
			return new CarritoResponse(items, total, count);
		}
	}
}
