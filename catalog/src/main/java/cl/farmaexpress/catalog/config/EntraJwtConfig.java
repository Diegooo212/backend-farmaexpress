package cl.farmaexpress.catalog.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Validación del JWT emitido por Microsoft Entra External ID (IDaaS):
 * <ul>
 * <li>firma RS256 con las llaves públicas del tenant (JWKS);</li>
 * <li>emisor ({@code iss}) igual al de nuestro tenant;</li>
 * <li>audiencia ({@code aud}) igual a nuestra API;</li>
 * <li>vigencia ({@code exp}, {@code nbf}).</li>
 * </ul>
 * Los claims {@code roles} (App Roles) y {@code scp} (scopes) se convierten en permisos de Spring.
 */
@Configuration
public class EntraJwtConfig {

	@Bean
	JwtDecoder jwtDecoder(@Value("${farmaexpress.entra.tenant-id}") String tenantId,
			@Value("${farmaexpress.entra.api-client-id}") String apiClientId,
			@Value("${farmaexpress.entra.issuer}") String issuer,
			@Value("${farmaexpress.entra.jwk-set-uri}") String jwkSetUri) {
		if (tenantId.isBlank() || apiClientId.isBlank()) {
			throw new IllegalStateException(
					"Falta configurar AZURE_TENANT_ID y AZURE_API_CLIENT_ID (tenant y App Registration de Entra ID).");
		}
		// Las llaves se descargan del tenant la primera vez que llega un token y quedan en caché.
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).jwsAlgorithm(SignatureAlgorithm.RS256).build();
		decoder.setJwtValidator(validador(issuer, apiClientId));
		return decoder;
	}

	/** Emisor, vigencia y audiencia. Se acepta el client id solo o con el prefijo api://. */
	public static OAuth2TokenValidator<Jwt> validador(String issuer, String apiClientId) {
		Set<String> audiencias = Set.of(apiClientId, "api://" + apiClientId);
		OAuth2TokenValidator<Jwt> audiencia = jwt -> jwt.getAudience() != null
				&& jwt.getAudience().stream().anyMatch(audiencias::contains)
						? OAuth2TokenValidatorResult.success()
						: OAuth2TokenValidatorResult.failure(
								new OAuth2Error("invalid_token", "El token no fue emitido para esta API (aud).", null));
		return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audiencia);
	}

	/** {@code scp: "access_as_user"} → SCOPE_access_as_user; {@code roles: ["Admin"]} → ROLE_Admin. */
	public static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
		JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
		roles.setAuthoritiesClaimName("roles");
		roles.setAuthorityPrefix("ROLE_");

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			Collection<GrantedAuthority> permisos = new ArrayList<>(scopes.convert(jwt));
			permisos.addAll(roles.convert(jwt));
			return permisos;
		});
		return converter;
	}
}
