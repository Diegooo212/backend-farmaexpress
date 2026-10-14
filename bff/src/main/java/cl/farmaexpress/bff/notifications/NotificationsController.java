package cl.farmaexpress.bff.notifications;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Avisos de la campana del frontend. El microservicio de notificaciones decide de quién son a
 * partir del JWT que se le reenvía.
 */
@RestController
@RequestMapping("/api/bff/notifications")
public class NotificationsController {

	private final NotificationsClient notifications;

	public NotificationsController(NotificationsClient notifications) {
		this.notifications = notifications;
	}

	@GetMapping
	public ResponseEntity<byte[]> listar(@AuthenticationPrincipal Jwt jwt) {
		return notifications.reenviar(HttpMethod.GET, "", jwt.getTokenValue());
	}

	@PutMapping("/leidas")
	public ResponseEntity<byte[]> marcarLeidas(@AuthenticationPrincipal Jwt jwt) {
		return notifications.reenviar(HttpMethod.PUT, "/leidas", jwt.getTokenValue());
	}
}
