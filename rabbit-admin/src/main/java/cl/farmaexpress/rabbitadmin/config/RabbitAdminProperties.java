package cl.farmaexpress.rabbitadmin.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del administrador (prefijo "farmaexpress.rabbit" en application.properties).
 *
 * @param managementUrl API HTTP de administración de RabbitMQ (se usa para listar).
 * @param vhost         host virtual que se administra.
 * @param dlx           exchange de mensajes fallidos para las DLQ de las colas nuevas.
 * @param protegidos    prefijos de colas y exchanges del sistema que no se pueden eliminar.
 */
@ConfigurationProperties(prefix = "farmaexpress.rabbit")
public record RabbitAdminProperties(String managementUrl, String vhost, String dlx, List<String> protegidos) {

	public boolean esProtegido(String nombre) {
		return protegidos.stream().anyMatch(prefijo -> nombre.equals(prefijo) || nombre.startsWith(prefijo));
	}
}
