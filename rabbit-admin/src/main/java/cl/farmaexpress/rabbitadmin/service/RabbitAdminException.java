package cl.farmaexpress.rabbitadmin.service;

import org.springframework.http.HttpStatus;

/** Error de negocio del administrador, con el estado HTTP que le corresponde y un mensaje claro. */
public class RabbitAdminException extends RuntimeException {

	private final HttpStatus status;

	public RabbitAdminException(HttpStatus status, String mensaje) {
		super(mensaje);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public static RabbitAdminException noExiste(String recurso, String nombre) {
		return new RabbitAdminException(HttpStatus.NOT_FOUND, "No existe " + recurso + " \"" + nombre + "\".");
	}

	public static RabbitAdminException yaExiste(String recurso, String nombre) {
		return new RabbitAdminException(HttpStatus.CONFLICT, "Ya existe " + recurso + " \"" + nombre + "\".");
	}

	public static RabbitAdminException protegido(String nombre) {
		return new RabbitAdminException(HttpStatus.CONFLICT,
				"\"" + nombre + "\" es parte del sistema (la usan los microservicios de FarmaExpress) y no se puede modificar.");
	}

	public static RabbitAdminException invalido(String mensaje) {
		return new RabbitAdminException(HttpStatus.BAD_REQUEST, mensaje);
	}
}
