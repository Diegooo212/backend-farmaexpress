package cl.farmaexpress.bff.auth;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El inicio de sesión lo hace Microsoft Entra ID (OIDC, Authorization Code + PKCE) en el frontend.
 * Aquí solo se entrega el perfil de quien llama, leído de su token validado.
 */
@RestController
@RequestMapping("/api/bff/auth")
public class AuthController {

	private final UsuarioService usuarios;

	public AuthController(UsuarioService usuarios) {
		this.usuarios = usuarios;
	}

	public record PerfilResponse(String id, String nombre, String email, List<String> roles, List<String> scopes) {
	}

	/** Registra el perfil la primera vez y lo actualiza en cada ingreso. */
	@GetMapping("/me")
	public PerfilResponse me(@AuthenticationPrincipal Jwt jwt) {
		UsuarioActual actual = UsuarioActual.desde(jwt);
		Usuario usuario = usuarios.sincronizar(actual);
		return new PerfilResponse(actual.oid(), usuario.getNombre(), usuario.getEmail(),
				List.of(usuario.getRol().name()), actual.scopes());
	}
}
