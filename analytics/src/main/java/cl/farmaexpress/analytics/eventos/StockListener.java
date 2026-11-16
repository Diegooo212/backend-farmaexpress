package cl.farmaexpress.analytics.eventos;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import cl.farmaexpress.analytics.config.KafkaConfig;
import cl.farmaexpress.analytics.service.AnaliticaService;

/**
 * Consumidor del tópico compactado {@code farmaexpress.stock} en el grupo {@code analytics-stock}.
 * La clave del mensaje es el producto: todos los cambios de un producto caen en la misma partición y se
 * procesan en orden. Un mensaje sin contenido (lápida) significa que el producto ya no tiene stock publicado.
 */
@Component
public class StockListener {

	private static final Logger log = LoggerFactory.getLogger(StockListener.class);

	private final AnaliticaService analitica;
	private final ValidadorEventos validador;

	public StockListener(AnaliticaService analitica, ValidadorEventos validador) {
		this.analitica = analitica;
		this.validador = validador;
	}

	@KafkaListener(topics = KafkaConfig.TOPICO_STOCK, groupId = KafkaConfig.GRUPO_STOCK,
			concurrency = "${farmaexpress.analytics.consumidores:3}",
			properties = "spring.json.value.default.type=cl.farmaexpress.analytics.eventos.StockActualizado")
	public void alActualizarseStock(ConsumerRecord<String, StockActualizado> registro) {
		log.debug("Evento de stock recibido de {}-{}@{} (clave {})", registro.topic(), registro.partition(), registro.offset(),
				registro.key());
		if (registro.value() == null) {
			analitica.eliminarStock(producto(registro.key()));
			return;
		}
		StockActualizado evento = validador.validar(registro.value());
		if (registro.key() != null && !registro.key().equals(String.valueOf(evento.medicamentoId()))) {
			throw new EventoInvalidoException("La clave del mensaje (" + registro.key() + ") no es el producto del evento ("
					+ evento.medicamentoId() + ").");
		}
		analitica.actualizarStock(evento, registro.partition(), registro.offset());
	}

	private static Long producto(String clave) {
		try {
			return Long.valueOf(clave);
		}
		catch (NumberFormatException | NullPointerException ex) {
			throw new EventoInvalidoException("Lápida con una clave que no es un producto: " + clave);
		}
	}
}
