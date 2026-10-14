package cl.farmaexpress.payments.proveedores;

import java.util.Map;

import cl.farmaexpress.payments.domain.MetodoPago;
import cl.farmaexpress.payments.domain.Pago;

/**
 * Adaptador de una pasarela de pago (patrón Strategy): cada proveedor sabe cómo crear la
 * transacción, a dónde mandar al cliente y cómo confirmar el resultado cuando vuelve.
 * Las llamadas pasan siempre por el circuit breaker de {@code PasarelaPagos}.
 */
public interface ProveedorPago {

	MetodoPago metodo();

	/** REAL si hay credenciales (API del proveedor); SIMULADO si no. */
	Pago.Modo modo();

	/**
	 * Crea la transacción en el proveedor.
	 *
	 * @param urlRetorno a dónde vuelve el navegador del cliente al terminar (incluye ?pago=id)
	 * @throws ProveedorNoDisponibleException si el proveedor no responde o responde con error 5xx
	 */
	Redireccion iniciar(Pago pago, String descripcion, String urlRetorno);

	/**
	 * Confirma con el proveedor el resultado del pago usando los parámetros con que volvió el navegador.
	 * Nunca se confía solo en esos parámetros: siempre se consulta al proveedor (o se valida el secreto
	 * del simulador).
	 */
	Resultado confirmar(Pago pago, Map<String, String> parametros);

	/** A dónde y cómo enviar el navegador del cliente (GET normal o formulario POST, como pide Webpay). */
	record Redireccion(String metodoHttp, String url, Map<String, String> campos, String tokenProveedor) {

		public static Redireccion get(String url, String tokenProveedor) {
			return new Redireccion("GET", url, Map.of(), tokenProveedor);
		}
	}

	/** {@code pendiente}: el proveedor aún no confirma (por ejemplo, una transferencia en verificación). */
	record Resultado(boolean aprobado, boolean pendiente, String referencia, String detalle) {

		public static Resultado aprobado(String referencia, String detalle) {
			return new Resultado(true, false, referencia, detalle);
		}

		public static Resultado rechazado(String detalle) {
			return new Resultado(false, false, null, detalle);
		}

		public static Resultado pendiente(String detalle) {
			return new Resultado(false, true, null, detalle);
		}
	}
}
