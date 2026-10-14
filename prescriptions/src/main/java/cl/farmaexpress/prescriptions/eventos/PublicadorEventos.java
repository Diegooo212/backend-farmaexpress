package cl.farmaexpress.prescriptions.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cl.farmaexpress.prescriptions.config.RabbitConfig;

/**
 * Envía los eventos de recetas a RabbitMQ <b>después</b> del commit: si la transacción falla no
 * se avisa nada, y si RabbitMQ no está disponible la receta igual queda guardada (solo se pierde
 * el aviso, que queda registrado en el log).
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

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publicar(RecetaEstadoCambiado evento) {
		if (!habilitado) {
			return;
		}
		try {
			rabbit.convertAndSend(RabbitConfig.EXCHANGE, RecetaEstadoCambiado.ROUTING_KEY, evento);
		}
		catch (AmqpException ex) {
			log.warn("No se pudo publicar {} de la receta {} en RabbitMQ: {}", RecetaEstadoCambiado.ROUTING_KEY,
					evento.recetaId(), ex.getMessage());
		}
	}
}
