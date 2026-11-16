package cl.farmaexpress.analytics.service;

import java.time.Instant;
import java.util.List;

/** Panel de analítica en tiempo real que consume el frontend. */
public record Tablero(Instant generadoEn, Resumen hoy, Resumen total, List<PuntoHora> ventasPorHora,
		List<ProductoVendido> masVendidos, List<Segmento> porSucursal, List<Segmento> porMetodoPago,
		List<VentaReciente> recientes, List<StockBajo> stockBajo, int umbralStockBajo) {

	/** @param ticketPromedio ingresos / ventas, en pesos */
	public record Resumen(long ventas, long ingresos, long unidades, long ticketPromedio) {
	}

	/** Ventas de una hora (inicio de la hora en UTC). */
	public record PuntoHora(Instant hora, long ventas, long ingresos) {
	}

	public record ProductoVendido(Long medicamentoId, String nombre, long unidades, long ingresos) {
	}

	public record Segmento(String nombre, long ventas, long ingresos) {
	}

	/**
	 * @param demoraMs tiempo entre la aprobación del pago y su procesamiento en la analítica
	 * @param particion partición de Kafka de la que se leyó
	 * @param offset posición del mensaje en esa partición
	 */
	public record VentaReciente(Long pedidoId, Instant fecha, Instant recibidaEn, long demoraMs, int total, int unidades,
			String entrega, String sucursal, String metodoPago, int particion, long offset) {
	}

	public record StockBajo(Long medicamentoId, int stockTotal, int stockPorPedido, Instant actualizadoEn) {
	}
}
