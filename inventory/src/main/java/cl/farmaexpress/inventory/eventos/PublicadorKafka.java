package cl.farmaexpress.inventory.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cl.farmaexpress.inventory.config.KafkaConfig;
import cl.farmaexpress.inventory.eventos.Eventos.StockCambiado;

/**
 * Productor Kafka de inventory: por cada cambio de stock publica, después del commit, un mensaje por
 * producto en el tópico compactado {@code farmaexpress.stock}. La clave es el id del producto: todos sus
 * cambios caen en la misma partición (quedan en orden) y la compactación conserva el último.
 * El envío es asíncrono y, si Kafka no está, solo queda en el log: el inventario sigue funcionando.
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

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void stockCambiado(StockCambiado cambio) {
		if (!habilitado || cambio.items() == null) {
			return;
		}
		for (Eventos.StockProducto producto : cambio.items()) {
			StockActualizado evento = StockActualizado.de(producto, cambio.fecha());
			String clave = String.valueOf(evento.medicamentoId());
			try {
				kafka.send(KafkaConfig.TOPICO_STOCK, clave, evento).whenComplete((resultado, error) -> {
					if (error != null) {
						log.error("No se pudo publicar el stock del producto {} en {}: {}", clave, KafkaConfig.TOPICO_STOCK,
								error.getMessage());
					}
					else {
						log.debug("Stock del producto {} publicado en {}-{}@{}", clave, resultado.getRecordMetadata().topic(),
								resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset());
					}
				});
			}
			catch (RuntimeException ex) {
				log.error("No se pudo publicar el stock del producto {} en {}: {}", clave, KafkaConfig.TOPICO_STOCK, ex.getMessage());
			}
		}
	}
}
