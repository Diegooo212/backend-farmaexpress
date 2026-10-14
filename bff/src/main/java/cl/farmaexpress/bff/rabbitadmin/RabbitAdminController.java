package cl.farmaexpress.bff.rabbitadmin;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import cl.farmaexpress.bff.web.Reenvio;

/**
 * Puerta del frontend al microservicio administrador de RabbitMQ (solo Administrador, ver
 * SecurityConfig). Reenvía método, ruta, parámetros, cuerpo y JWT tal cual:
 * /api/bff/admin/rabbit/queues → rabbit-admin /api/admin/rabbit/queues.
 */
@RestController
@RequestMapping("/api/bff/admin/rabbit")
public class RabbitAdminController {

	private static final String PREFIJO_BFF = "/api/bff/admin/rabbit";
	private static final String PREFIJO_SERVICIO = "/api/admin/rabbit";

	private final RestClient client;

	public RabbitAdminController(@Qualifier("rabbitAdminRestClient") RestClient client) {
		this.client = client;
	}

	@RequestMapping("/**")
	public ResponseEntity<byte[]> reenviar(HttpServletRequest request, @RequestBody(required = false) byte[] cuerpo,
			@AuthenticationPrincipal Jwt jwt) {
		String ruta = PREFIJO_SERVICIO + request.getRequestURI().substring(PREFIJO_BFF.length());
		if (request.getQueryString() != null) {
			ruta += "?" + request.getQueryString();
		}
		return Reenvio.reenviar(client, HttpMethod.valueOf(request.getMethod()), ruta,
				cuerpo == null || cuerpo.length == 0 ? null : cuerpo, MediaType.APPLICATION_JSON, jwt.getTokenValue());
	}
}
