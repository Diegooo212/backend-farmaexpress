package cl.farmaexpress.inventory.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cl.farmaexpress.inventory.config.RabbitConfig;
import cl.farmaexpress.inventory.eventos.Eventos.ReservaExpirada;
import cl.farmaexpress.inventory.eventos.Eventos.StockCambiado;

/**
 * Envía los eventos a RabbitMQ después del commit. Si RabbitMQ no está, el inventario sigue
 * funcionando: el catálogo se pone al día con el próximo cambio o al reiniciar el inventario.
 */
@Component
public class PublicadorEventos {

	private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

	private final RabbitTemplate rabbit;
	private final boolean habilitado;

	public PublicadorEventos(RabbitTemplate rabbit, @Value("${farmaexpress.eventos.habilitado}") boolean habilitado) {
		this.rabbit = rabbit;
		this.habilitado = habilitado;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void stockCambiado(StockCambiado evento) {
		enviar(StockCambiado.ROUTING_KEY, evento);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void reservaExpirada(ReservaExpirada evento) {
		enviar(ReservaExpirada.ROUTING_KEY, evento);
	}

	private void enviar(String routingKey, Object evento) {
		if (!habilitado) {
			return;
		}
		try {
			rabbit.convertAndSend(RabbitConfig.EXCHANGE, routingKey, evento);
		}
		catch (AmqpException ex) {
			log.warn("No se pudo publicar {} en RabbitMQ: {}", routingKey, ex.getMessage());
		}
	}
}
