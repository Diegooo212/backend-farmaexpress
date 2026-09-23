package cl.farmaexpress.catalog.web.dto;

import java.util.List;

import cl.farmaexpress.catalog.domain.Medicamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Contratos JSON del catálogo. Los nombres de campo coinciden con los que usa el frontend. */
public final class MedicamentoDtos {

	private MedicamentoDtos() {
	}

	public record MedicamentoRequest(
			@NotBlank(message = "El código (SKU) es obligatorio.")
			@Size(max = 40, message = "El código (SKU) admite hasta 40 caracteres.") String sku,
			@NotBlank(message = "El nombre es obligatorio.")
			@Size(max = 150, message = "El nombre admite hasta 150 caracteres.") String nombre,
			@NotNull(message = "El precio es obligatorio.")
			@PositiveOrZero(message = "El precio no puede ser negativo.") Integer precio,
			@NotNull(message = "El stock es obligatorio.")
			@PositiveOrZero(message = "El stock no puede ser negativo.") Integer stock) {
	}

	public record MedicamentoResponse(Long id, String sku, String nombre, Integer precio, Integer stock) {

		public static MedicamentoResponse from(Medicamento m) {
			return new MedicamentoResponse(m.getId(), m.getSku(), m.getNombre(), m.getPrecio(), m.getStock());
		}
	}

	public record StockItem(
			@NotNull(message = "Falta el id del medicamento.") Long id,
			@NotNull(message = "Falta la cantidad.")
			@Positive(message = "La cantidad debe ser mayor que cero.") Integer cantidad) {
	}

	public record DescontarStockRequest(
			@NotEmpty(message = "No hay productos para descontar.") List<@Valid StockItem> items) {
	}
}
