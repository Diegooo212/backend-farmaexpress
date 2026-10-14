package cl.farmaexpress.inventory.domain;

/** Resultado de consulta: unidades vendibles de un producto en una sucursal. */
public record StockPorSucursal(Long medicamentoId, Long sucursalId, Long disponible) {
}
