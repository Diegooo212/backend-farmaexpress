package cl.farmaexpress.bff.inventory;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import cl.farmaexpress.bff.web.Reenvio;

/** Cliente HTTP del microservicio de inventario. Siempre reenvía el JWT del usuario. */
@Component
public class InventoryClient {

	private static final String BASE = "/api/inventory";

	private final RestClient client;

	public InventoryClient(@Qualifier("inventoryRestClient") RestClient client) {
		this.client = client;
	}

	public record ItemReserva(Long medicamentoId, Integer cantidad, String nombre) {
	}

	record ReservaRequest(String referencia, Long sucursalId, List<ItemReserva> items) {
	}

	public record Sucursal(Long id, String codigo, String nombre, String direccion, String comuna, String tipo) {
	}

	public record Reserva(Long id, String referencia, String estado, Sucursal sucursal, String expiraEn) {
	}

	/** Reserva el stock del pedido con FEFO (todo o nada). {@code sucursalId} null = despacho desde el central. */
	public Reserva reservar(String referencia, Long sucursalId, List<ItemReserva> items, String token) {
		return client.post()
			.uri(BASE + "/reservas")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.body(new ReservaRequest(referencia, sucursalId, items))
			.retrieve()
			.body(Reserva.class);
	}

	public void liberar(Long reservaId, String token) {
		client.delete()
			.uri(BASE + "/reservas/{id}", reservaId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.retrieve()
			.toBodilessEntity();
	}

	/** Reenvío tal cual para las consultas de la tienda y la gestión de lotes. */
	public ResponseEntity<byte[]> reenviar(HttpMethod method, String ruta, String jsonBody, String token) {
		return Reenvio.reenviar(client, method, BASE + ruta, jsonBody, MediaType.APPLICATION_JSON, token);
	}
}
