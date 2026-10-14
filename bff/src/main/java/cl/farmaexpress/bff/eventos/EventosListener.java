package cl.farmaexpress.bff.eventos;

import java.time.Instant;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.farmaexpress.bff.config.RabbitConfig;
import cl.farmaexpress.bff.pedidos.PedidoService;

/**
 * Consumidores del dominio "pedidos": actualizan el estado de cada pedido con lo que informan
 * payments e inventory. Cada {@code @RabbitListener} confirma el mensaje de forma explícita (ACK
 * manual) a través de {@link ConfirmacionMensajes}; la lógica está en los métodos de negocio.
 */
@Component
public class EventosListener {

	/** Publicado por payments. */
	public record PagoAprobado(String eventoId, Long pagoId, Long pedidoId, Long reservaId, String cuentaId,
			Integer monto, String metodo, String referencia, Instant fecha) {
	}

	/** Publicado por payments. */
	public record PagoRechazado(String eventoId, Long pagoId, Long pedidoId, Long reservaId, String cuentaId,
			Integer monto, String metodo, String motivo, Instant fecha) {
	}

	/** Publicado por inventory. */
	public record ReservaExpirada(String eventoId, Long reservaId, String referencia, Instant fecha) {
	}

	private final PedidoService pedidos;
	private final ConfirmacionMensajes confirmacion;

	public EventosListener(PedidoService pedidos, ConfirmacionMensajes confirmacion) {
		this.pedidos = pedidos;
		this.confirmacion = confirmacion;
	}

	// ------------------------------------------------------------ Consumidores (ACK manual)

	@RabbitListener(queues = RabbitConfig.COLA_APROBADOS)
	public void alAprobarsePago(PagoAprobado evento, Message mensaje, Channel canal) {
		confirmacion.procesar(mensaje, canal, () -> pagoAprobado(evento));
	}

	@RabbitListener(queues = RabbitConfig.COLA_RECHAZADOS)
	public void alRechazarsePago(PagoRechazado evento, Message mensaje, Channel canal) {
		confirmacion.procesar(mensaje, canal, () -> pagoRechazado(evento));
	}

	@RabbitListener(queues = RabbitConfig.COLA_RESERVAS)
	public void alExpirarReserva(ReservaExpirada evento, Message mensaje, Channel canal) {
		confirmacion.procesar(mensaje, canal, () -> reservaExpirada(evento));
	}

	// ------------------------------------------------------------ Negocio

	public void pagoAprobado(PagoAprobado evento) {
		requerido(evento.pedidoId(), "pago.aprobado sin pedidoId (pago " + evento.pagoId() + ")");
		pedidos.pagoAprobado(evento.pedidoId(), evento.pagoId(), evento.metodo(), evento.monto());
	}

	public void pagoRechazado(PagoRechazado evento) {
		requerido(evento.pedidoId(), "pago.rechazado sin pedidoId (pago " + evento.pagoId() + ")");
		pedidos.pagoRechazado(evento.pedidoId(), evento.pagoId(), evento.motivo());
	}

	public void reservaExpirada(ReservaExpirada evento) {
		requerido(evento.referencia(), "reserva.expirada sin referencia (reserva " + evento.reservaId() + ")");
		pedidos.reservaExpirada(evento.referencia());
	}

	/** Un evento incompleto no se puede procesar nunca: error no recuperable (va directo a la DLQ). */
	private static void requerido(Object valor, String mensaje) {
		if (valor == null) {
			throw new IllegalArgumentException(mensaje);
		}
	}
}
