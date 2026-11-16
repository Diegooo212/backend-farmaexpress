package cl.farmaexpress.bff.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cl.farmaexpress.bff.config.KafkaConfig;

/**
 * Productor Kafka del BFF: publica cada venta confirmada en {@code farmaexpress.ventas} después del commit
 * (nunca se anuncia una venta que no quedó guardada). La clave es el id del pedido: todos los eventos de un
 * mismo pedido caen en la misma partición y el consumidor puede descartar repetidos.
 *
 * <p>El envío es asíncrono: el pedido no espera a Kafka. Con acks=all e idempotencia (application.properties)
 * el productor reintenta solo ante fallas transitorias sin duplicar mensajes; si aun así no logra publicar,
 * lo deja en el log (el pedido ya quedó pagado: la analítica es un efecto secundario).
 */
@Component
public class PublicadorKafka {

	private static final Logger log = LoggerFactory.getLogger(PublicadorKafka.class);

	private final KafkaTemplate<String, Object> kafka;
	private final boolean habilitado;

	public PublicadorKafka(KafkaTemplate<String, Object> kafka, @Value("${farmaexpress.kafka.habilitado}") boolean habilitado) {
		this.kafka = kafka;
		this.habilitado = habilitado;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void ventaRegistrada(VentaRegistrada venta) {
		if (!habilitado) {
			return;
		}
		String clave = String.valueOf(venta.pedidoId());
		try {
			kafka.send(KafkaConfig.TOPICO_VENTAS, clave, venta).whenComplete((resultado, error) -> {
				if (error != null) {
					log.error("No se pudo publicar la venta del pedido {} en {}: {}", venta.pedidoId(), KafkaConfig.TOPICO_VENTAS,
							error.getMessage());
				}
				else {
					log.info("Venta del pedido {} publicada en {}-{}@{}", venta.pedidoId(), resultado.getRecordMetadata().topic(),
							resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset());
				}
			});
		}
		catch (RuntimeException ex) {
			// Sin brokers disponibles el envío falla de inmediato (max.block.ms): el pedido sigue su curso.
			log.error("No se pudo publicar la venta del pedido {} en {}: {}", venta.pedidoId(), KafkaConfig.TOPICO_VENTAS,
					ex.getMessage());
		}
	}
}
