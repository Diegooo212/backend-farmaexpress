package cl.farmaexpress.inventory.config;

import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAnyRole;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;
import static org.springframework.security.authorization.AuthorizationManagers.allOf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Consultas de stock públicas (no revelan datos personales); reservas con el token del cliente;
 * lotes y resumen solo para el personal de farmacia (Operador o Administrador).
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${farmaexpress.entra.scope}") String scope)
			throws Exception {
		AuthorizationManager<RequestAuthorizationContext> conScope = hasAuthority("SCOPE_" + scope);
		AuthorizationManager<RequestAuthorizationContext> personal = allOf(conScope, hasAnyRole("Operador", "Admin"));

		http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/inventory/sucursales", "/api/inventory/disponibilidad/*").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/inventory/cobertura").permitAll()
				.requestMatchers("/api/inventory/reservas/**").access(conScope)
				.requestMatchers("/api/inventory/lotes/**", "/api/inventory/resumen").access(personal)
				.anyRequest().denyAll())
			.oauth2ResourceServer(oauth -> oauth
				.jwt(jwt -> jwt.jwtAuthenticationConverter(EntraJwtConfig.jwtAuthenticationConverter()))
				.authenticationEntryPoint(RespuestasSeguridad.noAutenticado())
				.accessDeniedHandler(RespuestasSeguridad.sinPermiso()));
		return http.build();
	}
}
