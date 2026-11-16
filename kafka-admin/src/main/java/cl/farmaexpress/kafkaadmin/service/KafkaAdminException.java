package cl.farmaexpress.kafkaadmin.service;

import org.springframework.http.HttpStatus;

/** Error de negocio del administrador, con el estado HTTP que le corresponde y un mensaje claro. */
public class KafkaAdminException extends RuntimeException {

	private final HttpStatus status;

	public KafkaAdminException(HttpStatus status, String mensaje) {
		super(mensaje);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public static KafkaAdminException noExiste(String recurso, String nombre) {
		return new KafkaAdminException(HttpStatus.NOT_FOUND, "No existe " + recurso + " \"" + nombre + "\".");
	}

	public static KafkaAdminException conflicto(String mensaje) {
		return new KafkaAdminException(HttpStatus.CONFLICT, mensaje);
	}

	public static KafkaAdminException invalido(String mensaje) {
		return new KafkaAdminException(HttpStatus.BAD_REQUEST, mensaje);
	}
}
