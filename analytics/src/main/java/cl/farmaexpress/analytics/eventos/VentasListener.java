package cl.farmaexpress.analytics.eventos;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import cl.farmaexpress.analytics.config.KafkaConfig;
import cl.farmaexpress.analytics.service.AnaliticaService;

/**
 * Consumidor del tópico {@code farmaexpress.ventas} en el grupo {@code analytics-ventas}. Corre 3 hilos
 * consumidores (uno por partición): Kafka reparte las particiones entre ellos y, si uno falla, sus
 * particiones pasan a los otros. Solo valida y delega: la lógica está en {@link AnaliticaService}.
 */
@Component
public class VentasListener {

	private static final Logger log = LoggerFactory.getLogger(VentasListener.class);

	private final AnaliticaService analitica;
	private final ValidadorEventos validador;

	public VentasListener(AnaliticaService analitica, ValidadorEventos validador) {
		this.analitica = analitica;
		this.validador = validador;
	}

	@KafkaListener(topics = KafkaConfig.TOPICO_VENTAS, groupId = KafkaConfig.GRUPO_VENTAS,
			concurrency = "${farmaexpress.analytics.consumidores:3}",
			properties = "spring.json.value.default.type=cl.farmaexpress.analytics.eventos.VentaRegistrada")
	public void alRegistrarseVenta(ConsumerRecord<String, VentaRegistrada> registro) {
		log.debug("Evento de venta recibido de {}-{}@{} (clave {})", registro.topic(), registro.partition(), registro.offset(),
				registro.key());
		VentaRegistrada venta = validador.validarVenta(registro.value());
		if (registro.key() != null && !registro.key().equals(String.valueOf(venta.pedidoId()))) {
			throw new EventoInvalidoException("La clave del mensaje (" + registro.key() + ") no es el pedido del evento ("
					+ venta.pedidoId() + ").");
		}
		analitica.registrarVenta(venta, registro.partition(), registro.offset());
	}
}
