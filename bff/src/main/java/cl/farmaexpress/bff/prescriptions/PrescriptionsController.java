package cl.farmaexpress.bff.prescriptions;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Recetas para el frontend. Quién ve qué (paciente solo lo suyo, personal todo) lo decide
 * el microservicio de recetas a partir del JWT que se le reenvía.
 */
@RestController
@RequestMapping("/api/bff/prescriptions")
public class PrescriptionsController {

	private final PrescriptionsClient prescriptions;

	public PrescriptionsController(PrescriptionsClient prescriptions) {
		this.prescriptions = prescriptions;
	}

	@GetMapping
	public ResponseEntity<byte[]> listar(@RequestParam(required = false) String status, @AuthenticationPrincipal Jwt jwt) {
		String ruta = UriComponentsBuilder.fromPath("")
			.queryParamIfPresent("status", Optional.ofNullable(status).filter(s -> !s.isBlank()))
			.build().encode().toUriString();
		return prescriptions.reenviar(HttpMethod.GET, ruta, null, jwt.getTokenValue());
	}

	@GetMapping("/{id}")
	public ResponseEntity<byte[]> obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return prescriptions.reenviar(HttpMethod.GET, "/" + id, null, jwt.getTokenValue());
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<byte[]> crear(@RequestPart("datos") String datos,
			@RequestPart(value = "archivo", required = false) MultipartFile archivo,
			@AuthenticationPrincipal Jwt jwt) throws IOException {
		return prescriptions.crear(datos, archivo, jwt.getTokenValue());
	}

	@PutMapping("/{id}/status")
	public ResponseEntity<byte[]> cambiarEstado(@PathVariable Long id, @RequestBody String body,
			@AuthenticationPrincipal Jwt jwt) {
		return prescriptions.reenviar(HttpMethod.PUT, "/" + id + "/status", body, jwt.getTokenValue());
	}

	@GetMapping("/{id}/archivo")
	public ResponseEntity<byte[]> archivo(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return prescriptions.reenviar(HttpMethod.GET, "/" + id + "/archivo", null, jwt.getTokenValue());
	}
}
