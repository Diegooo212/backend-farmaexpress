package cl.farmaexpress.payments.proveedores;

import java.util.function.Supplier;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** Traduce los errores de red y 5xx de un proveedor a {@link ProveedorNoDisponibleException}. */
final class LlamadasHttp {

	private LlamadasHttp() {
	}

	static <T> T llamar(String proveedor, Supplier<T> llamada) {
		try {
			return llamada.get();
		}
		catch (ResourceAccessException ex) {
			throw new ProveedorNoDisponibleException(proveedor + " no respondió a tiempo", ex);
		}
		catch (HttpServerErrorException ex) {
			throw new ProveedorNoDisponibleException(proveedor + " respondió " + ex.getStatusCode(), ex);
		}
	}
}
