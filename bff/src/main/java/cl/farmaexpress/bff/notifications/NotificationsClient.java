package cl.farmaexpress.bff.notifications;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import cl.farmaexpress.bff.web.Reenvio;

/** Cliente HTTP del microservicio de notificaciones. Siempre reenvía el JWT del usuario. */
@Component
public class NotificationsClient {

	private static final String AVISOS = "/api/notifications";

	private final RestClient client;

	public NotificationsClient(@Qualifier("notificationsRestClient") RestClient client) {
		this.client = client;
	}

	public ResponseEntity<byte[]> reenviar(HttpMethod method, String ruta, String token) {
		return Reenvio.reenviar(client, method, AVISOS + ruta, null, MediaType.APPLICATION_JSON, token);
	}
}
