package cl.farmaexpress.payments.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cl.farmaexpress.payments.config.RabbitConfig;
import cl.farmaexpress.payments.eventos.Eventos.PagoAprobado;
import cl.farmaexpress.payments.eventos.Eventos.PagoRechazado;

/** Publica el resultado de cada pago en RabbitMQ, después de guardarlo. */
@Component
public class PublicadorEventos {

	private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

	private final RabbitTemplate rabbit;
	private final boolean habilitado;

	public PublicadorEventos(RabbitTemplate rabbit, @Value("${farmaexpress.eventos.habilitado}") boolean habilitado) {
		this.rabbit = rabbit;
		this.habilitado = habilitado;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void aprobado(PagoAprobado evento) {
		enviar(PagoAprobado.ROUTING_KEY, evento, evento.pagoId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void rechazado(PagoRechazado evento) {
		enviar(PagoRechazado.ROUTING_KEY, evento, evento.pagoId());
	}

	private void enviar(String routingKey, Object evento, Long pagoId) {
		if (!habilitado) {
			return;
		}
		try {
			rabbit.convertAndSend(RabbitConfig.EXCHANGE, routingKey, evento);
		}
		catch (AmqpException ex) {
			// El pago ya quedó guardado; el pedido se puede reconciliar consultando GET /api/payments/{id}.
			log.error("No se pudo publicar {} del pago {} en RabbitMQ: {}", routingKey, pagoId, ex.getMessage());
		}
	}

}
