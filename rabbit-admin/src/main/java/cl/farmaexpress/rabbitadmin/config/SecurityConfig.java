package cl.farmaexpress.rabbitadmin.config;

import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasRole;
import static org.springframework.security.authorization.AuthorizationManagers.allOf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Solo un Administrador (App Role "Admin" de Entra ID) con el scope de la API puede administrar
 * RabbitMQ. Igual que el resto de los microservicios, valida por su cuenta el JWT.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${farmaexpress.entra.scope}") String scope)
			throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
				// Página de error interna: así un fallo inesperado responde su código real y no un 403.
				.requestMatchers("/error").permitAll()
				.requestMatchers("/api/admin/rabbit/**").access(allOf(hasAuthority("SCOPE_" + scope), hasRole("Admin")))
				.anyRequest().denyAll())
			.oauth2ResourceServer(oauth -> oauth
				.jwt(jwt -> jwt.jwtAuthenticationConverter(EntraJwtConfig.jwtAuthenticationConverter()))
				.authenticationEntryPoint(RespuestasSeguridad.noAutenticado())
				.accessDeniedHandler(RespuestasSeguridad.sinPermiso()));
		return http.build();
	}
}
