package cl.farmaexpress.notifications.config;

import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Igual que el resto de los microservicios, valida por su cuenta el JWT de Entra ID que le reenvía
 * el BFF. Cada persona solo ve sus propios avisos (se filtra por el oid del token en el servicio).
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
				.requestMatchers("/api/notifications/**").access(hasAuthority("SCOPE_" + scope))
				.anyRequest().denyAll())
			.oauth2ResourceServer(oauth -> oauth
				.jwt(jwt -> jwt.jwtAuthenticationConverter(EntraJwtConfig.jwtAuthenticationConverter()))
				.authenticationEntryPoint(RespuestasSeguridad.noAutenticado())
				.accessDeniedHandler(RespuestasSeguridad.sinPermiso()));
		return http.build();
	}
}
