package cl.farmaexpress.notifications.eventos;

import java.time.Instant;

/** Evento {@code receta.estado-cambiado} que publica el microservicio de recetas. */
public record RecetaEstadoCambiado(String eventoId, Long recetaId, String cuentaId, String estado, String nota,
		String metodoDespacho, String farmacia, Instant fecha) {
}
