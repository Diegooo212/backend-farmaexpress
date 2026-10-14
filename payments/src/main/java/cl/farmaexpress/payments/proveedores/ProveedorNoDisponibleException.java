package cl.farmaexpress.payments.proveedores;

/**
 * El proveedor no respondió, se demoró demasiado o respondió con un error de su lado (5xx).
 * Es la falla que cuenta el circuit breaker; un pago rechazado por el banco NO es una falla.
 */
public class ProveedorNoDisponibleException extends RuntimeException {

	public ProveedorNoDisponibleException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}

	public ProveedorNoDisponibleException(String mensaje) {
		super(mensaje);
	}
}
