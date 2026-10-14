package cl.farmaexpress.bff.cuenta;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cl.farmaexpress.bff.auth.UsuarioActual;
import cl.farmaexpress.bff.auth.UsuarioService;
import cl.farmaexpress.bff.cuenta.CuentaService.DireccionRequest;
import cl.farmaexpress.bff.cuenta.CuentaService.DireccionResponse;
import cl.farmaexpress.bff.cuenta.CuentaService.PerfilRequest;
import cl.farmaexpress.bff.cuenta.CuentaService.PerfilResponse;

/** Mi cuenta. Siempre opera sobre la cuenta del token (nunca recibe un id de usuario). */
@RestController
@RequestMapping("/api/bff/account")
public class CuentaController {

	private final CuentaService cuenta;
	private final UsuarioService usuarios;

	public CuentaController(CuentaService cuenta, UsuarioService usuarios) {
		this.cuenta = cuenta;
		this.usuarios = usuarios;
	}

	private Long usuarioId(Jwt jwt) {
		return usuarios.sincronizar(UsuarioActual.desde(jwt)).getId();
	}

	@GetMapping("/perfil")
	public PerfilResponse perfil(@AuthenticationPrincipal Jwt jwt) {
		return cuenta.perfil(usuarioId(jwt));
	}

	@PutMapping("/perfil")
	public PerfilResponse actualizarPerfil(@Valid @RequestBody PerfilRequest request, @AuthenticationPrincipal Jwt jwt) {
		return cuenta.actualizarPerfil(usuarioId(jwt), request);
	}

	@GetMapping("/direcciones")
	public List<DireccionResponse> direcciones(@AuthenticationPrincipal Jwt jwt) {
		return cuenta.direcciones(usuarioId(jwt));
	}

	@PostMapping("/direcciones")
	@ResponseStatus(HttpStatus.CREATED)
	public DireccionResponse agregar(@Valid @RequestBody DireccionRequest request, @AuthenticationPrincipal Jwt jwt) {
		return cuenta.agregar(usuarioId(jwt), request);
	}

	@PutMapping("/direcciones/{id}")
	public DireccionResponse actualizar(@PathVariable Long id, @Valid @RequestBody DireccionRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return cuenta.actualizar(usuarioId(jwt), id, request);
	}

	@GetMapping("/favoritos")
	public List<Long> favoritos(@AuthenticationPrincipal Jwt jwt) {
		return cuenta.favoritos(usuarioId(jwt));
	}

	@PutMapping("/favoritos/{medicamentoId}")
	public List<Long> agregarFavorito(@PathVariable Long medicamentoId, @AuthenticationPrincipal Jwt jwt) {
		return cuenta.agregarFavorito(usuarioId(jwt), medicamentoId);
	}

	@DeleteMapping("/favoritos/{medicamentoId}")
	public List<Long> quitarFavorito(@PathVariable Long medicamentoId, @AuthenticationPrincipal Jwt jwt) {
		return cuenta.quitarFavorito(usuarioId(jwt), medicamentoId);
	}

	@DeleteMapping("/direcciones/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		cuenta.eliminar(usuarioId(jwt), id);
	}
}
