package cl.farmaexpress.bff.analytics;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import cl.farmaexpress.bff.web.Reenvio;

/**
 * Puerta del frontend a la analítica en tiempo real (Operador y Administrador, ver SecurityConfig):
 * /api/bff/analytics/tablero → analytics /api/analytics/tablero.
 */
@RestController
@RequestMapping("/api/bff/analytics")
public class AnalyticsController {

	private static final String PREFIJO_BFF = "/api/bff/analytics";
	private static final String PREFIJO_SERVICIO = "/api/analytics";

	private final RestClient client;

	public AnalyticsController(@Qualifier("analyticsRestClient") RestClient client) {
		this.client = client;
	}

	@GetMapping("/**")
	public ResponseEntity<byte[]> reenviar(HttpServletRequest request, @AuthenticationPrincipal Jwt jwt) {
		String ruta = PREFIJO_SERVICIO + request.getRequestURI().substring(PREFIJO_BFF.length());
		return Reenvio.reenviar(client, HttpMethod.GET, ruta, null, MediaType.APPLICATION_JSON, jwt.getTokenValue());
	}
}
