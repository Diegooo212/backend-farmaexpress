package cl.farmaexpress.catalog.config;

import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasRole;
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
 * El catálogo valida por su cuenta el JWT de Entra ID que le reenvía el BFF, así nadie puede
 * saltarse el BFF o el API Gateway para cambiar precios o stock.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${farmaexpress.entra.scope}") String scope)
			throws Exception {
		AuthorizationManager<RequestAuthorizationContext> conScope = hasAuthority("SCOPE_" + scope);

		http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
				// Cualquier usuario con sesión puede comprar (descontar stock).
				.requestMatchers(HttpMethod.POST, "/api/catalog/medicamentos/descontar-stock").access(conScope)
				.requestMatchers("/api/catalog/**").access(allOf(conScope, hasRole("Admin")))
				.anyRequest().denyAll())
			.oauth2ResourceServer(oauth -> oauth
				.jwt(jwt -> jwt.jwtAuthenticationConverter(EntraJwtConfig.jwtAuthenticationConverter()))
				.authenticationEntryPoint(RespuestasSeguridad.noAutenticado())
				.accessDeniedHandler(RespuestasSeguridad.sinPermiso()));
		return http.build();
	}
}
