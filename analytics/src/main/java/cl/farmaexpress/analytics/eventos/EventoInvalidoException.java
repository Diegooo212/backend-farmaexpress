package cl.farmaexpress.analytics.eventos;

/**
 * El evento llegó bien formado como JSON, pero sus datos no cumplen el contrato (campos faltantes, totales
 * que no cuadran, versión desconocida). Reintentar no lo arregla: el manejador de errores lo envía
 * directo al dead letter topic, sin reintentos.
 */
public class EventoInvalidoException extends RuntimeException {

	public EventoInvalidoException(String mensaje) {
		super(mensaje);
	}
}
