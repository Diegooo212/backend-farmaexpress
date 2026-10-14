package cl.farmaexpress.notifications.service;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.farmaexpress.notifications.domain.Notificacion;
import cl.farmaexpress.notifications.domain.NotificacionRepository;
import cl.farmaexpress.notifications.eventos.PagoAprobado;
import cl.farmaexpress.notifications.eventos.PagoRechazado;
import cl.farmaexpress.notifications.eventos.PedidoEstadoCambiado;
import cl.farmaexpress.notifications.eventos.RecetaEstadoCambiado;

@Service
public class NotificacionService {

	private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
	private static final Locale CHILE = Locale.of("es", "CL");

	private final NotificacionRepository notificaciones;

	public NotificacionService(NotificacionRepository notificaciones) {
		this.notificaciones = notificaciones;
	}

	public record NotificacionResponse(Long id, String tipo, Long referenciaId, String estado, String titulo,
			String mensaje, String detalle, boolean leida, String fecha) {

		static NotificacionResponse from(Notificacion n) {
			return new NotificacionResponse(n.getId(), n.getTipo(), n.getReferenciaId(), n.getEstado(), n.getTitulo(),
					n.getMensaje(), n.getDetalle(), n.isLeida(), n.getFecha().toString());
		}
	}

	/** Aviso al paciente cuando la farmacia cambia el estado de su receta. */
	@Transactional
	public void registrar(RecetaEstadoCambiado e) {
		guardar(e.eventoId(), e.cuentaId(), "RECETA", e.recetaId(), e.estado(), "Receta #" + e.recetaId(),
				mensajeReceta(e), e.nota(), e.fecha());
	}

	/** Aviso al cliente: su pago se aprobó y el pedido está en preparación. */
	@Transactional
	public void registrar(PagoAprobado e) {
		String mensaje = "Pago aprobado por %s con %s. Estamos preparando tu pedido.".formatted(pesos(e.monto()),
				nombreMetodo(e.metodo()));
		guardar(e.eventoId(), e.cuentaId(), "PEDIDO", e.pedidoId(), "PAGO_APROBADO", "Pedido #" + e.pedidoId(), mensaje,
				null, e.fecha());
	}

	/** Aviso al cliente: el pago no pasó; puede reintentar con otro medio mientras dure la reserva. */
	@Transactional
	public void registrar(PagoRechazado e) {
		String mensaje = "Tu pago con %s no se completó. Puedes intentarlo con otro medio de pago.".formatted(
				nombreMetodo(e.metodo()));
		guardar(e.eventoId(), e.cuentaId(), "PEDIDO", e.pedidoId(), "PAGO_RECHAZADO", "Pedido #" + e.pedidoId(), mensaje,
				e.motivo(), e.fecha());
	}

	/** Avisos del seguimiento del pedido: en preparación, listo para retiro, en camino, entregado. */
	@Transactional
	public void registrar(PedidoEstadoCambiado e) {
		String mensaje = switch (e.estado()) {
			case "EN_PREPARACION" -> "Estamos preparando tu pedido.";
			case "LISTO_RETIRO" -> "Tu pedido está listo para retiro en "
					+ (e.sucursal() != null ? e.sucursal() : "la farmacia") + ". Lleva tu carnet de identidad.";
			case "EN_CAMINO" -> "Tu pedido va en camino a tu domicilio.";
			case "ENTREGADO" -> "Tu pedido fue entregado. ¡Gracias por comprar en FarmaExpress!";
			default -> "Tu pedido cambió a " + e.estado() + ".";
		};
		guardar(e.eventoId(), e.cuentaId(), "PEDIDO", e.pedidoId(), "PEDIDO_" + e.estado(), "Pedido #" + e.pedidoId(),
				mensaje, e.nota(), e.fecha());
	}

	@Transactional(readOnly = true)
	public List<NotificacionResponse> listar(String cuentaId) {
		return notificaciones.findTop30ByCuentaIdOrderByFechaDescIdDesc(cuentaId).stream()
			.map(NotificacionResponse::from)
			.toList();
	}

	@Transactional
	public void marcarLeidas(String cuentaId) {
		notificaciones.findAllByCuentaIdAndLeidaFalse(cuentaId).forEach(Notificacion::marcarLeida);
	}

	private void guardar(String eventoId, String cuentaId, String tipo, Long referenciaId, String estado, String titulo,
			String mensaje, String detalle, Instant fecha) {
		if (eventoId == null || cuentaId == null) {
			// Un evento sin destinatario no se puede entregar: al lanzar la excepción termina en la DLQ.
			throw new IllegalArgumentException("Evento " + tipo + " sin eventoId o sin cuentaId");
		}
		// RabbitMQ entrega "al menos una vez": si el mismo evento llega de nuevo, no se duplica el aviso.
		if (notificaciones.existsByEventoId(eventoId)) {
			log.info("Evento {} ya procesado, se ignora", eventoId);
			return;
		}
		notificaciones.save(new Notificacion(eventoId, cuentaId, tipo, referenciaId, estado, titulo, mensaje, detalle,
				fecha != null ? fecha : Instant.now()));
	}

	/** Mismos textos que muestra el frontend en "Mis recetas". */
	private static String mensajeReceta(RecetaEstadoCambiado e) {
		return switch (e.estado()) {
			case "VALIDADA" -> "Un químico farmacéutico validó tu receta.";
			case "EN_PREPARACION" -> "Estamos preparando tu receta.";
			case "LISTA_RETIRO" -> "DESPACHO_DOMICILIO".equals(e.metodoDespacho())
					? "Tu receta está lista y saldrá a despacho."
					: "Tu receta está lista para retiro en " + (e.farmacia() != null ? e.farmacia() : "la farmacia") + ".";
			case "DISPENSADA" -> "Tu receta fue entregada.";
			case "RECHAZADA" -> "Tu receta fue rechazada.";
			default -> "Tu receta cambió a " + e.estado() + ".";
		};
	}

	private static String nombreMetodo(String metodo) {
		return switch (metodo == null ? "" : metodo) {
			case "WEBPAY" -> "Webpay";
			case "MERCADOPAGO" -> "Mercado Pago";
			case "KHIPU" -> "Khipu";
			default -> "tu medio de pago";
		};
	}

	private static String pesos(Integer monto) {
		NumberFormat formato = NumberFormat.getIntegerInstance(CHILE);
		return "$" + formato.format(monto == null ? 0 : monto);
	}
}
