package cl.farmaexpress.catalog.eventos;

/**
 * El evento de Kafka llegó bien formado pero sus datos no sirven (faltan campos o cantidades inválidas).
 * Reintentar no lo arregla: va directo al dead letter topic.
 */
public class EventoInvalidoException extends RuntimeException {

	public EventoInvalidoException(String mensaje) {
		super(mensaje);
	}
}
