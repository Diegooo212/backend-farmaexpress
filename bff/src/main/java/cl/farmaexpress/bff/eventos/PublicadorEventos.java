package cl.farmaexpress.bff.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cl.farmaexpress.bff.config.RabbitConfig;

/**
 * Envía a RabbitMQ los cambios de estado de los pedidos, después del commit. Si RabbitMQ no está, el
 * pedido igual cambia de estado (solo se pierde el aviso, que queda en el log).
 */
@Component
public class PublicadorEventos {

	private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

	private final RabbitTemplate rabbit;
	private final boolean habilitado;

	public PublicadorEventos(RabbitTemplate rabbit, @Value("${farmaexpress.eventos.habilitado:true}") boolean habilitado) {
		this.rabbit = rabbit;
		this.habilitado = habilitado;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void pedidoEstadoCambiado(PedidoEstadoCambiado evento) {
		if (!habilitado) {
			return;
		}
		try {
			rabbit.convertAndSend(RabbitConfig.EXCHANGE, PedidoEstadoCambiado.ROUTING_KEY, evento);
		}
		catch (AmqpException ex) {
			log.warn("No se pudo publicar {} del pedido {} en RabbitMQ: {}", PedidoEstadoCambiado.ROUTING_KEY,
					evento.pedidoId(), ex.getMessage());
		}
	}
}
