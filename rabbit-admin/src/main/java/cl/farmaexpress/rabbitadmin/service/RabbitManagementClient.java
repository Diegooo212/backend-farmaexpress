package cl.farmaexpress.rabbitadmin.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import cl.farmaexpress.rabbitadmin.config.RabbitAdminProperties;

/**
 * Lectura del estado de RabbitMQ con su API HTTP de administración (plugin management).
 * Solo consulta: crear y eliminar se hace por AMQP en {@link RabbitAdminService}.
 */
@Component
public class RabbitManagementClient {

	private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA = new ParameterizedTypeReference<>() {
	};

	private final RestClient client;
	private final String vhost;

	public RabbitManagementClient(@Qualifier("management") RestClient client, RabbitAdminProperties props) {
		this.client = client;
		// Se pasa como variable de la URL: RestClient lo codifica una sola vez ("/" → %2F).
		this.vhost = props.vhost();
	}

	public List<Map<String, Object>> colas() {
		return listar("/api/queues/{vhost}");
	}

	public List<Map<String, Object>> exchanges() {
		return listar("/api/exchanges/{vhost}");
	}

	public List<Map<String, Object>> bindings() {
		return listar("/api/bindings/{vhost}");
	}

	public Optional<Map<String, Object>> exchange(String nombre) {
		try {
			return Optional.ofNullable(client.get()
				.uri("/api/exchanges/{vhost}/{nombre}", vhost, nombre)
				.retrieve()
				.body(new ParameterizedTypeReference<Map<String, Object>>() {
				}));
		}
		catch (HttpClientErrorException.NotFound e) {
			return Optional.empty();
		}
		catch (ResourceAccessException e) {
			throw sinConexion();
		}
	}

	private List<Map<String, Object>> listar(String ruta) {
		try {
			List<Map<String, Object>> lista = client.get().uri(ruta, vhost).retrieve().body(LISTA);
			return lista == null ? List.of() : lista;
		}
		catch (ResourceAccessException e) {
			throw sinConexion();
		}
		catch (RestClientResponseException e) {
			throw respuestaInesperada(e);
		}
	}

	private static RabbitAdminException respuestaInesperada(RestClientResponseException e) {
		return new RabbitAdminException(HttpStatus.BAD_GATEWAY,
				"La API de administración de RabbitMQ respondió " + e.getStatusCode().value() + ".");
	}

	private static RabbitAdminException sinConexion() {
		return new RabbitAdminException(HttpStatus.SERVICE_UNAVAILABLE,
				"No se pudo conectar con la API de administración de RabbitMQ (puerto 15672).");
	}
}
