package cl.farmaexpress.catalog.eventos;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import cl.farmaexpress.catalog.config.KafkaConfig;
import cl.farmaexpress.catalog.service.VentasService;

/**
 * Consumidor Kafka del catálogo (grupo {@code catalog-ventas}): por cada venta confirmada suma las unidades
 * vendidas de cada producto. Lee solo los campos que necesita del evento (el resto se ignora), así el BFF
 * puede agregar campos nuevos sin romper este consumidor.
 */
@Component
public class VentasKafkaListener {

	private static final Logger log = LoggerFactory.getLogger(VentasKafkaListener.class);

	/** Vista del evento farmaexpress.ventas con lo que usa el catálogo. */
	public record VentaRegistrada(@NotNull Long pedidoId, @NotEmpty List<@Valid @NotNull Item> items) {

		public record Item(@NotNull Long medicamentoId, @NotNull @Positive Integer cantidad) {
		}
	}

	private final VentasService ventas;
	private final Validator validator;

	public VentasKafkaListener(VentasService ventas, Validator validator) {
		this.ventas = ventas;
		this.validator = validator;
	}

	@KafkaListener(topics = KafkaConfig.TOPICO_VENTAS, groupId = KafkaConfig.GRUPO_VENTAS,
			concurrency = "${farmaexpress.kafka.consumidores:3}",
			properties = "spring.json.value.default.type=cl.farmaexpress.catalog.eventos.VentasKafkaListener$VentaRegistrada")
	public void alRegistrarseVenta(ConsumerRecord<String, VentaRegistrada> registro) {
		log.debug("Venta recibida de {}-{}@{} (clave {})", registro.topic(), registro.partition(), registro.offset(), registro.key());
		VentaRegistrada venta = validar(registro.value());
		Map<Long, Integer> unidades = venta.items().stream()
			.collect(Collectors.toMap(VentaRegistrada.Item::medicamentoId, VentaRegistrada.Item::cantidad, Integer::sum));
		ventas.registrarVenta(venta.pedidoId(), unidades);
	}

	private VentaRegistrada validar(VentaRegistrada venta) {
		if (venta == null) {
			throw new EventoInvalidoException("El mensaje no trae contenido.");
		}
		Set<ConstraintViolation<VentaRegistrada>> errores = validator.validate(venta);
		if (!errores.isEmpty()) {
			throw new EventoInvalidoException(errores.stream()
				.sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
				.map(v -> v.getPropertyPath() + " " + v.getMessage())
				.collect(Collectors.joining("; ")));
		}
		return venta;
	}
}
