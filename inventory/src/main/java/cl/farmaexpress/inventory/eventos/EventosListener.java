package cl.farmaexpress.inventory.eventos;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.farmaexpress.inventory.config.RabbitConfig;
import cl.farmaexpress.inventory.eventos.Eventos.PagoAprobado;
import cl.farmaexpress.inventory.service.InventarioService;

/**
 * Consumidor del dominio "reservas": cuando un pago se aprueba, la reserva de ese pedido se
 * confirma (se descuentan los lotes, FEFO). El mensaje se confirma de forma explícita (ACK manual).
 */
@Component
public class EventosListener {

	private final InventarioService inventario;
	private final ConfirmacionMensajes confirmacion;

	public EventosListener(InventarioService inventario, ConfirmacionMensajes confirmacion) {
		this.inventario = inventario;
		this.confirmacion = confirmacion;
	}

	@RabbitListener(queues = RabbitConfig.COLA_PAGOS)
	public void alAprobarsePago(PagoAprobado evento, Message mensaje, Channel canal) {
		confirmacion.procesar(mensaje, canal, () -> pagoAprobado(evento));
	}

	public void pagoAprobado(PagoAprobado evento) {
		if (evento.reservaId() == null) {
			// Sin reserva no hay nada que confirmar: error no recuperable (va directo a la DLQ).
			throw new IllegalArgumentException("pago.aprobado sin reservaId (pago " + evento.pagoId() + ")");
		}
		inventario.confirmar(evento.reservaId(), "pago " + evento.pagoId());
	}
}
