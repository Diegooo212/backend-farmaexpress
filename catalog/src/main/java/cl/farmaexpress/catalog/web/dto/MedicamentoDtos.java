package cl.farmaexpress.catalog.web.dto;

import java.util.List;

import cl.farmaexpress.catalog.domain.Categoria;
import cl.farmaexpress.catalog.domain.Medicamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
			// El stock real lo informa el inventario por lotes; aquí solo se acepta para no romper clientes antiguos.
			@PositiveOrZero(message = "El stock no puede ser negativo.") Integer stock,
			Long categoriaId,
			@Size(max = 1000, message = "La descripción admite hasta 1000 caracteres.") String descripcion,
			@Size(max = 100, message = "El laboratorio admite hasta 100 caracteres.") String laboratorio,
			@Size(max = 150, message = "El principio activo admite hasta 150 caracteres.") String principioActivo,
			@Size(max = 100, message = "La presentación admite hasta 100 caracteres.") String presentacion,
			Boolean requiereReceta,
			Boolean bioequivalente,
			@Size(max = 500, message = "La URL de la imagen es muy larga.")
			@Pattern(regexp = "^$|^https://.+", message = "La imagen debe ser una URL que empiece con https://.") String imagenUrl) {
	}

	public record CategoriaResponse(Long id, String slug, String nombre, String descripcion, String icono, long productos) {

		public static CategoriaResponse from(Categoria c, long productos) {
			return new CategoriaResponse(c.getId(), c.getSlug(), c.getNombre(), c.getDescripcion(), c.getIcono(), productos);
		}
	}

	public record MedicamentoResponse(Long id, String sku, String nombre, Integer precio, Integer stock, Integer stockTotal,
			Long categoriaId, String categoria, String categoriaSlug, String descripcion, String laboratorio,
			String principioActivo, String presentacion, boolean requiereReceta, boolean bioequivalente, String imagenUrl) {

		public static MedicamentoResponse from(Medicamento m) {
			Categoria c = m.getCategoria();
			return new MedicamentoResponse(m.getId(), m.getSku(), m.getNombre(), m.getPrecio(), m.getStock(),
					m.getStockTotal(), c != null ? c.getId() : null, c != null ? c.getNombre() : null,
					c != null ? c.getSlug() : null, m.getDescripcion(), m.getLaboratorio(), m.getPrincipioActivo(),
					m.getPresentacion(), m.isRequiereReceta(), m.isBioequivalente(), m.getImagenUrl());
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
