package cl.farmaexpress.inventory.web.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import cl.farmaexpress.inventory.domain.Sucursal;

public final class InventarioDtos {

	private InventarioDtos() {
	}

	public record SucursalResponse(Long id, String codigo, String nombre, String direccion, String comuna, String tipo) {

		public static SucursalResponse from(Sucursal s) {
			return new SucursalResponse(s.getId(), s.getCodigo(), s.getNombre(), s.getDireccion(), s.getComuna(),
					s.getTipo().name());
		}
	}

	/** Unidades vendibles de un producto en cada sucursal (ficha del producto). */
	public record DisponibilidadResponse(Long sucursalId, String sucursal, String tipo, int disponible) {
	}

	public record ItemSolicitado(@NotNull(message = "Falta el producto.") Long medicamentoId,
			@NotNull @Positive(message = "La cantidad debe ser mayor que cero.") Integer cantidad, String nombre) {
	}

	/** ¿Qué sucursales pueden entregar el carrito completo? (checkout). */
	public record CoberturaRequest(@NotEmpty(message = "El carrito está vacío.") List<@Valid ItemSolicitado> items) {
	}

	public record Faltante(Long medicamentoId, String nombre, int solicitado, int disponible) {
	}

	public record CoberturaResponse(Long sucursalId, String nombre, String tipo, String direccion, String comuna,
			boolean completo, List<Faltante> faltantes) {
	}

	/**
	 * Reserva del stock de un pedido. {@code sucursalId} es la farmacia de retiro; si viene vacío,
	 * el pedido es con despacho y sale del almacén central.
	 */
	public record ReservaRequest(@NotBlank(message = "Falta la referencia del pedido.") String referencia, Long sucursalId,
			@NotEmpty(message = "El pedido no tiene productos.") List<@Valid ItemSolicitado> items) {
	}

	public record ReservaItemResponse(Long medicamentoId, Long loteId, String numeroLote, LocalDate fechaVencimiento,
			int cantidad) {
	}

	public record ReservaResponse(Long id, String referencia, String estado, SucursalResponse sucursal, String expiraEn,
			List<ReservaItemResponse> items) {
	}

	public record LoteRequest(@NotNull(message = "Elige el producto.") Long medicamentoId,
			@NotNull(message = "Elige la sucursal.") Long sucursalId,
			@NotBlank(message = "Escribe el número de lote.") @Size(max = 40, message = "El número de lote es muy largo.") String numeroLote,
			@NotNull(message = "Indica la fecha de vencimiento.") LocalDate fechaVencimiento,
			@NotNull @Positive(message = "La cantidad debe ser mayor que cero.") Integer cantidad) {
	}

	public record LoteResponse(Long id, Long medicamentoId, Long sucursalId, String sucursal, String numeroLote,
			LocalDate fechaVencimiento, long diasParaVencer, int cantidadInicial, int disponible, int reservada,
			String estado) {
	}

	public record MermaRequest(@NotNull @Positive(message = "La cantidad debe ser mayor que cero.") Integer cantidad,
			@NotBlank(message = "Indica el motivo de la baja.") @Size(max = 120) String motivo) {
	}

	public record MovimientoResponse(Long id, String tipo, int cantidad, String referencia, String usuario, String fecha) {
	}

	public record ResumenResponse(int lotesVigentes, int unidadesVendibles, int lotesPorVencer, int unidadesPorVencer,
			int lotesVencidos, int unidadesVencidas, long reservasActivas, int diasAlerta) {
	}
}
