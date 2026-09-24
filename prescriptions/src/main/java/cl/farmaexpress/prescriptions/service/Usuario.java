package cl.farmaexpress.prescriptions.service;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

import cl.farmaexpress.prescriptions.config.EntraJwtConfig;

/**
 * Quién hace la petición, leído del JWT de Entra ID.
 * {@code id} es el {@code oid}: identificador único y estable de la persona en el tenant.
 */
public record Usuario(String id, String email, String nombre, List<String> roles) {

	public static Usuario desde(Jwt jwt) {
		String oid = jwt.getClaimAsString("oid");
		String email = primero(jwt, "email", "preferred_username", "upn");
		String nombre = primero(jwt, "name");
		List<String> roles = jwt.getClaimAsStringList("roles");
		// "admin", "Admin" u "ADMIN" son el mismo App Role.
		List<String> normalizados = roles == null ? List.of()
				: roles.stream().map(EntraJwtConfig::rolNormalizado).toList();
		return new Usuario(oid != null ? oid : jwt.getSubject(), email,
				nombre != null ? nombre : email, normalizados);
	}

	/** Operadores y administradores ven y gestionan todas las recetas. */
	public boolean esPersonalFarmacia() {
		return roles.contains("Operador") || roles.contains("Admin");
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
}
