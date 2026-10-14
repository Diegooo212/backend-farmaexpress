package cl.farmaexpress.bff.catalog;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
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

	/**
	 * Lo que el BFF necesita de cada producto. El catálogo envía más datos (ficha, stockTotal...), que aquí
	 * se ignoran. {@code requiereReceta}: venta bajo receta médica.
	 */
	@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
	public record Medicamento(Long id, String sku, String nombre, Integer precio, Integer stock, Boolean requiereReceta) {

		public Medicamento(Long id, String sku, String nombre, Integer precio, Integer stock) {
			this(id, sku, nombre, precio, stock, false);
		}

		public boolean conReceta() {
			return Boolean.TRUE.equals(requiereReceta);
		}
	}

	public List<Medicamento> listar() {
		return client.get().uri(MEDICAMENTOS).retrieve().body(new ParameterizedTypeReference<>() {
		});
	}

	public ResponseEntity<byte[]> categorias() {
		return Reenvio.reenviar(client, HttpMethod.GET, "/api/catalog/categorias", null, MediaType.APPLICATION_JSON, null);
	}

	public ResponseEntity<byte[]> reenviar(HttpMethod method, String ruta, String jsonBody, String token) {
		return Reenvio.reenviar(client, method, MEDICAMENTOS + ruta, jsonBody, MediaType.APPLICATION_JSON, token);
	}
}
