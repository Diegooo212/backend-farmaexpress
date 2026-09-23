package cl.farmaexpress.bff.config;

import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAnyRole;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasRole;
import static org.springframework.security.authorization.AuthorizationManagers.allOf;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * El BFF valida el access token de Entra ID en cada petición (firma, iss, aud, vigencia) y
 * autoriza por scope ({@code access_as_user}) y por App Role ({@code Operador}, {@code Admin}).
 * Mismas reglas que las rutas de API Gateway, para que el BFF quede protegido aunque lo llamen directo.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${farmaexpress.entra.scope}") String scope)
			throws Exception {
		AuthorizationManager<RequestAuthorizationContext> conScope = hasAuthority("SCOPE_" + scope);

		http
			// API sin sesión ni cookies: el JWT viaja en el header Authorization, así que CSRF no aplica.
			.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/bff/catalog/**").permitAll()
				.requestMatchers("/api/bff/catalog/**").access(allOf(conScope, hasRole("Admin")))
				.requestMatchers(HttpMethod.PUT, "/api/bff/prescriptions/*/status")
					.access(allOf(conScope, hasAnyRole("Operador", "Admin")))
				.requestMatchers("/api/bff/**").access(conScope)
				.anyRequest().denyAll())
			.oauth2ResourceServer(oauth -> oauth
				.jwt(jwt -> jwt.jwtAuthenticationConverter(EntraJwtConfig.jwtAuthenticationConverter()))
				.authenticationEntryPoint(RespuestasSeguridad.noAutenticado())
				.accessDeniedHandler(RespuestasSeguridad.sinPermiso()));
		return http.build();
	}

	/** CORS para desarrollo local (el frontend llama directo al BFF). En la nube lo hace API Gateway. */
	@Bean
	CorsConfigurationSource corsConfigurationSource(FarmaexpressProperties props) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(props.cors().allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
		config.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.WWW_AUTHENTICATE));
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}
}
