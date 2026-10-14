package cl.farmaexpress.prescriptions.eventos;

import java.time.Instant;

import cl.farmaexpress.prescriptions.config.RabbitConfig;

/**
 * Evento que se publica en RabbitMQ (exchange {@code farmaexpress.eventos}, routing key
 * {@code receta.estado-cambiado}) cada vez que la farmacia cambia el estado de una receta.
 * Lo consume el microservicio de notificaciones para avisarle al paciente.
 *
 * @param eventoId identificador único del evento: el consumidor lo usa para no duplicar avisos
 * @param cuentaId oid de Entra ID del paciente dueño de la receta
 */
public record RecetaEstadoCambiado(String eventoId, Long recetaId, String cuentaId, String estado, String nota,
		String metodoDespacho, String farmacia, Instant fecha) {

	public static final String ROUTING_KEY = RabbitConfig.RK_RECETA_ESTADO_CAMBIADO;
}
