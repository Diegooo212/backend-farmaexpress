package cl.farmaexpress.notifications.eventos;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.farmaexpress.notifications.config.RabbitConfig;
import cl.farmaexpress.notifications.service.NotificacionService;

/**
 * Consumidores del dominio "avisos al cliente". Cada {@code @RabbitListener} confirma el mensaje de
 * forma explícita (ACK manual) a través de {@link ConfirmacionMensajes}: si el procesamiento falla,
 * se reintenta solo los errores recuperables y el resto va a la DLQ de su cola, registrado en el log.
 */
@Component
public class EventosListener {

	private final NotificacionService notificaciones;
	private final ConfirmacionMensajes confirmacion;

	public EventosListener(NotificacionService notificaciones, ConfirmacionMensajes confirmacion) {
		this.notificaciones = notificaciones;
		this.confirmacion = confirmacion;
	}

	// ------------------------------------------------------------ Consumidores (ACK manual)

	@RabbitListener(queues = RabbitConfig.COLA_RECETAS)
	public void alCambiarReceta(RecetaEstadoCambiado evento, Message mensaje, Channel canal) {
		confirmacion.procesar(mensaje, canal, () -> recetaEstadoCambiado(evento));
	}

	@RabbitListener(queues = RabbitConfig.COLA_PAGOS_APROBADOS)
	public void alAprobarsePago(PagoAprobado evento, Message mensaje, Channel canal) {
		confirmacion.procesar(mensaje, canal, () -> pagoAprobado(evento));
	}

	@RabbitListener(queues = RabbitConfig.COLA_PAGOS_RECHAZADOS)
	public void alRechazarsePago(PagoRechazado evento, Message mensaje, Channel canal) {
		confirmacion.procesar(mensaje, canal, () -> pagoRechazado(evento));
	}

	@RabbitListener(queues = RabbitConfig.COLA_PEDIDOS)
	public void alCambiarPedido(PedidoEstadoCambiado evento, Message mensaje, Channel canal) {
		confirmacion.procesar(mensaje, canal, () -> pedidoEstadoCambiado(evento));
	}

	// ------------------------------------------------------------ Negocio

	public void recetaEstadoCambiado(RecetaEstadoCambiado evento) {
		notificaciones.registrar(evento);
	}

	public void pagoAprobado(PagoAprobado evento) {
		notificaciones.registrar(evento);
	}

	public void pagoRechazado(PagoRechazado evento) {
		notificaciones.registrar(evento);
	}

	public void pedidoEstadoCambiado(PedidoEstadoCambiado evento) {
		notificaciones.registrar(evento);
	}
}
