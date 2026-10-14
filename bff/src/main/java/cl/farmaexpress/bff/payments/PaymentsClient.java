package cl.farmaexpress.bff.payments;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import cl.farmaexpress.bff.web.Reenvio;

/** Cliente HTTP del microservicio de pagos. */
@Component
public class PaymentsClient {

	private static final String BASE = "/api/payments";

	private final RestClient client;

	public PaymentsClient(@Qualifier("paymentsRestClient") RestClient client) {
		this.client = client;
	}

	record IniciarPago(Long pedidoId, Long reservaId, Integer monto, String metodo, String descripcion) {
	}

	public record Redireccion(String metodo, String url, Map<String, String> campos) {
	}

	public record PagoIniciado(Long pagoId, Long pedidoId, String estado, String metodo, String modo,
			Redireccion redireccion) {
	}

	/** Crea el pago en el proveedor elegido. Responde 503 si el proveedor no está disponible (circuit breaker). */
	public PagoIniciado iniciar(Long pedidoId, Long reservaId, int monto, String metodo, String descripcion, String token) {
		return client.post()
			.uri(BASE)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.body(new IniciarPago(pedidoId, reservaId, monto, metodo, descripcion))
			.retrieve()
			.body(PagoIniciado.class);
	}

	public ResponseEntity<byte[]> reenviar(HttpMethod method, String ruta, String jsonBody, String token) {
		return Reenvio.reenviar(client, method, BASE + ruta, jsonBody, MediaType.APPLICATION_JSON, token);
	}

	/**
	 * Regreso del cliente desde el proveedor (sin token). Los parámetros (query o formulario POST de
	 * Webpay) se pasan como query string; pagos responde 303 con la página del frontend.
	 */
	public ResponseEntity<byte[]> retorno(String metodo, MultiValueMap<String, String> parametros) {
		String ruta = UriComponentsBuilder.fromPath(BASE + "/retorno/{metodo}")
			.queryParams(parametros)
			.buildAndExpand(metodo)
			.encode()
			.toUriString();
		return Reenvio.reenviar(client, HttpMethod.GET, ruta, null, MediaType.APPLICATION_JSON, null);
	}
}
