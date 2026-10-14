package cl.farmaexpress.bff.inventory;

import java.util.Optional;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Inventario para el frontend: consultas públicas de la tienda (sucursales, disponibilidad y
 * cobertura del carrito) y gestión de lotes para el personal de farmacia.
 */
@RestController
@RequestMapping("/api/bff/inventory")
public class InventoryController {

	private final InventoryClient inventory;

	public InventoryController(InventoryClient inventory) {
		this.inventory = inventory;
	}

	private static String token(Jwt jwt) {
		return jwt != null ? jwt.getTokenValue() : null;
	}

	@GetMapping("/sucursales")
	public ResponseEntity<byte[]> sucursales() {
		return inventory.reenviar(HttpMethod.GET, "/sucursales", null, null);
	}

	@GetMapping("/disponibilidad/{medicamentoId}")
	public ResponseEntity<byte[]> disponibilidad(@PathVariable Long medicamentoId) {
		return inventory.reenviar(HttpMethod.GET, "/disponibilidad/" + medicamentoId, null, null);
	}

	@PostMapping("/cobertura")
	public ResponseEntity<byte[]> cobertura(@RequestBody String body) {
		return inventory.reenviar(HttpMethod.POST, "/cobertura", body, null);
	}

	@GetMapping("/lotes")
	public ResponseEntity<byte[]> lotes(@RequestParam(required = false) Long sucursalId,
			@RequestParam(required = false) Long medicamentoId, @RequestParam(required = false) String estado,
			@AuthenticationPrincipal Jwt jwt) {
		String ruta = UriComponentsBuilder.fromPath("/lotes")
			.queryParamIfPresent("sucursalId", Optional.ofNullable(sucursalId))
			.queryParamIfPresent("medicamentoId", Optional.ofNullable(medicamentoId))
			.queryParamIfPresent("estado", Optional.ofNullable(estado).filter(e -> !e.isBlank()))
			.build().encode().toUriString();
		return inventory.reenviar(HttpMethod.GET, ruta, null, token(jwt));
	}

	@PostMapping("/lotes")
	public ResponseEntity<byte[]> ingresarLote(@RequestBody String body, @AuthenticationPrincipal Jwt jwt) {
		return inventory.reenviar(HttpMethod.POST, "/lotes", body, token(jwt));
	}

	@PostMapping("/lotes/{id}/merma")
	public ResponseEntity<byte[]> darDeBaja(@PathVariable Long id, @RequestBody String body,
			@AuthenticationPrincipal Jwt jwt) {
		return inventory.reenviar(HttpMethod.POST, "/lotes/" + id + "/merma", body, token(jwt));
	}

	@GetMapping("/lotes/{id}/movimientos")
	public ResponseEntity<byte[]> movimientos(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return inventory.reenviar(HttpMethod.GET, "/lotes/" + id + "/movimientos", null, token(jwt));
	}

	@GetMapping("/resumen")
	public ResponseEntity<byte[]> resumen(@AuthenticationPrincipal Jwt jwt) {
		return inventory.reenviar(HttpMethod.GET, "/resumen", null, token(jwt));
	}
}
