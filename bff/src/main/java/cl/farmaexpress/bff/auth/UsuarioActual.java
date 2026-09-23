package cl.farmaexpress.bff.auth;

import java.util.Arrays;
import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

/** Quién hace la petición, leído del access token de Entra ID ya validado. */
public record UsuarioActual(String oid, String email, String nombre, List<String> roles, List<String> scopes) {

	public static UsuarioActual desde(Jwt jwt) {
		String oid = jwt.getClaimAsString("oid");
		String email = normalizar(primero(jwt, "email", "preferred_username", "upn"));
		String nombre = primero(jwt, "name");
		List<String> roles = jwt.getClaimAsStringList("roles");
		String scp = jwt.getClaimAsString("scp");
		return new UsuarioActual(
				oid != null ? oid : jwt.getSubject(),
				email,
				nombre != null ? nombre : email != null ? email : "Usuario",
				roles != null ? roles : List.of(),
				scp != null ? Arrays.asList(scp.split(" ")) : List.of());
	}

	/** Admin pesa más que Operador; sin App Role, la persona es Cliente. */
	public Rol rol() {
		if (roles.stream().anyMatch("Admin"::equalsIgnoreCase)) {
			return Rol.Admin;
		}
		if (roles.stream().anyMatch("Operador"::equalsIgnoreCase)) {
			return Rol.Operador;
		}
		return Rol.Cliente;
	}

	private static String primero(Jwt jwt, String... claims) {
		for (String claim : claims) {
			String valor = jwt.getClaimAsString(claim);
			if (valor != null && !valor.isBlank()) {
				return valor.trim();
			}
		}
		return null;
	}

	private static String normalizar(String email) {
		return email != null && email.contains("@") ? email.toLowerCase() : null;
	}
}
