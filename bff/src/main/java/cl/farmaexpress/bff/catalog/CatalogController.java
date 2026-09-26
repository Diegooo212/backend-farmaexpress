package cl.farmaexpress.bff.catalog;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo para el frontend. Ver es público; crear, editar y eliminar exige el rol Admin. */
@RestController
@RequestMapping("/api/bff/catalog/medicamentos")
public class CatalogController {

	private final CatalogClient catalog;

	public CatalogController(CatalogClient catalog) {
		this.catalog = catalog;
	}

	@GetMapping
	public ResponseEntity<byte[]> listar() {
		return catalog.reenviar(HttpMethod.GET, "", null, null);
	}

	@GetMapping("/{id}")
	public ResponseEntity<byte[]> obtener(@PathVariable Long id) {
		return catalog.reenviar(HttpMethod.GET, "/" + id, null, null);
	}

	@PostMapping
	public ResponseEntity<byte[]> crear(@RequestBody String body, @AuthenticationPrincipal Jwt jwt) {
		return catalog.reenviar(HttpMethod.POST, "", body, jwt.getTokenValue());
	}

	@PutMapping("/{id}")
	public ResponseEntity<byte[]> actualizar(@PathVariable Long id, @RequestBody String body,
			@AuthenticationPrincipal Jwt jwt) {
		return catalog.reenviar(HttpMethod.PUT, "/" + id, body, jwt.getTokenValue());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<byte[]> eliminar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return catalog.reenviar(HttpMethod.DELETE, "/" + id, null, jwt.getTokenValue());
	}
}
