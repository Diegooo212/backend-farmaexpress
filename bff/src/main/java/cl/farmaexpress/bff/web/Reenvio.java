package cl.farmaexpress.bff.web;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Reenvía una petición a un microservicio y devuelve su respuesta tal cual (estado, cuerpo y
 * headers relevantes). Así los mensajes de error del servicio (Problem Details) llegan al frontend.
 */
public final class Reenvio {

	private static final List<String> HEADERS_QUE_PASAN = List.of(
			HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.CACHE_CONTROL);

	private Reenvio() {
	}

	public static ResponseEntity<byte[]> reenviar(RestClient client, HttpMethod method, String uri, Object body,
			MediaType contentType, String token) {
		RestClient.RequestBodySpec spec = client.method(method).uri(uri);
		if (token != null) {
			spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		if (body != null) {
			spec.contentType(contentType).body(body);
		}
		return spec.exchange((request, response) -> {
			HttpHeaders headers = new HttpHeaders();
			HEADERS_QUE_PASAN.forEach(nombre -> {
				List<String> valores = response.getHeaders().get(nombre);
				if (valores != null) {
					headers.put(nombre, valores);
				}
			});
			byte[] cuerpo = response.getBody().readAllBytes();
			return new ResponseEntity<>(cuerpo, headers, response.getStatusCode());
		});
	}
}
