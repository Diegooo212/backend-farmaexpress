package cl.farmaexpress.bff.catalog;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import cl.farmaexpress.bff.web.Reenvio;

/** Cliente HTTP del microservicio de catálogo. */
@Component
public class CatalogClient {

	private static final String MEDICAMENTOS = "/api/catalog/medicamentos";

	private final RestClient client;

	public CatalogClient(@Qualifier("catalogRestClient") RestClient client) {
		this.client = client;
	}

	public record Medicamento(Long id, String sku, String nombre, Integer precio, Integer stock) {
	}

	public record StockItem(Long id, Integer cantidad) {
	}

	record DescontarStock(List<StockItem> items) {
	}

	public List<Medicamento> listar() {
		return client.get().uri(MEDICAMENTOS).retrieve().body(new ParameterizedTypeReference<>() {
		});
	}

	/** Descuenta el stock de una compra; si falta stock, el catálogo responde 409 y no descuenta nada. */
	public List<Medicamento> descontarStock(List<StockItem> items, String token) {
		return client.post()
			.uri(MEDICAMENTOS + "/descontar-stock")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.body(new DescontarStock(items))
			.retrieve()
			.body(new ParameterizedTypeReference<>() {
			});
	}

	public ResponseEntity<byte[]> reenviar(HttpMethod method, String ruta, String jsonBody, String token) {
		return Reenvio.reenviar(client, method, MEDICAMENTOS + ruta, jsonBody, MediaType.APPLICATION_JSON, token);
	}
}
