package cl.farmaexpress.notifications.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cl.farmaexpress.notifications.service.NotificacionService;
import cl.farmaexpress.notifications.service.NotificacionService.NotificacionResponse;

/** Avisos de la persona que hace la petición: se identifica por el oid del JWT, nunca por un parámetro. */
@RestController
@RequestMapping("/api/notifications")
public class NotificacionController {

	private final NotificacionService notificaciones;

	public NotificacionController(NotificacionService notificaciones) {
		this.notificaciones = notificaciones;
	}

	@GetMapping
	public List<NotificacionResponse> listar(@AuthenticationPrincipal Jwt jwt) {
		return notificaciones.listar(cuentaId(jwt));
	}

	@PutMapping("/leidas")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void marcarLeidas(@AuthenticationPrincipal Jwt jwt) {
		notificaciones.marcarLeidas(cuentaId(jwt));
	}

	private static String cuentaId(Jwt jwt) {
		String oid = jwt.getClaimAsString("oid");
		return oid != null ? oid : jwt.getSubject();
	}
}
