package cl.farmaexpress.prescriptions.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

import cl.farmaexpress.prescriptions.domain.EstadoReceta;
import cl.farmaexpress.prescriptions.service.RecetaService;
import cl.farmaexpress.prescriptions.service.RecetaService.ArchivoDescarga;
import cl.farmaexpress.prescriptions.service.Usuario;
import cl.farmaexpress.prescriptions.web.dto.RecetaDtos.CambioEstadoRequest;
import cl.farmaexpress.prescriptions.web.dto.RecetaDtos.RecetaRequest;
import cl.farmaexpress.prescriptions.web.dto.RecetaDtos.RecetaResponse;

@RestController
@RequestMapping("/api/prescriptions")
public class RecetaController {

	private final RecetaService service;

	public RecetaController(RecetaService service) {
		this.service = service;
	}

	@GetMapping
	public List<RecetaResponse> listar(@RequestParam(required = false) EstadoReceta status,
			@AuthenticationPrincipal Jwt jwt) {
		return service.listar(Usuario.desde(jwt), status);
	}

	@GetMapping("/{id}")
	public RecetaResponse obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return service.obtener(id, Usuario.desde(jwt));
	}

	/** multipart/form-data con dos partes: "datos" (JSON del formulario) y "archivo" (foto o PDF). */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<RecetaResponse> crear(@Valid @RequestPart("datos") RecetaRequest datos,
			@RequestPart("archivo") MultipartFile archivo, @AuthenticationPrincipal Jwt jwt) {
		RecetaResponse creada = service.crear(datos, archivo, Usuario.desde(jwt));
		return ResponseEntity.created(URI.create("/api/prescriptions/" + creada.id())).body(creada);
	}

	@PutMapping("/{id}/status")
	public RecetaResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest cambio,
			@AuthenticationPrincipal Jwt jwt) {
		return service.cambiarEstado(id, cambio, Usuario.desde(jwt));
	}

	@GetMapping("/{id}/archivo")
	public ResponseEntity<byte[]> archivo(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		ArchivoDescarga archivo = service.archivo(id, Usuario.desde(jwt));
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(archivo.tipo()))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
			// Es un dato de salud: que no quede guardado en cachés intermedias.
			.cacheControl(CacheControl.noStore())
			.body(archivo.contenido());
	}
}
