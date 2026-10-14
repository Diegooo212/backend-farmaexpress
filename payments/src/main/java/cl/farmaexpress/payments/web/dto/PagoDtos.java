package cl.farmaexpress.payments.web.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import cl.farmaexpress.payments.domain.MetodoPago;
import cl.farmaexpress.payments.domain.Pago;

public final class PagoDtos {

	private PagoDtos() {
	}

	/** Lo envía el BFF al confirmar el checkout. */
	public record IniciarPagoRequest(@NotNull(message = "Falta el pedido.") Long pedidoId, Long reservaId,
			@NotNull @Positive(message = "El monto debe ser mayor que cero.") Integer monto,
			@NotNull(message = "Elige un medio de pago.") MetodoPago metodo,
			@Size(max = 120) String descripcion) {
	}

	public record RedireccionResponse(String metodo, String url, Map<String, String> campos) {
	}

	public record PagoIniciadoResponse(Long pagoId, Long pedidoId, String estado, String metodo, String modo,
			RedireccionResponse redireccion) {
	}

	public record PagoResponse(Long id, Long pedidoId, int monto, String metodo, String modo, String estado,
			String referencia, String detalle, String creadoEn, String actualizadoEn) {

		public static PagoResponse from(Pago p) {
			return new PagoResponse(p.getId(), p.getPedidoId(), p.getMonto(), p.getMetodo().name(), p.getModo().name(),
					p.getEstado().name(), p.getReferenciaProveedor(), p.getDetalle(), p.getCreadoEn().toString(),
					p.getActualizadoEn().toString());
		}
	}

	/** Estado de cada medio de pago para el checkout (incluye el estado de su circuit breaker). */
	public record MetodoResponse(String codigo, String nombre, String descripcion, String modo, boolean disponible,
			String circuito, boolean caidaSimulada) {
	}

	public record SimulacionRequest(@NotNull MetodoPago metodo, boolean caido) {
	}
}
