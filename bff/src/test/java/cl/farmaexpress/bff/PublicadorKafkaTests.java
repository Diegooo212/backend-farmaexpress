package cl.farmaexpress.bff;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import cl.farmaexpress.bff.config.KafkaConfig;
import cl.farmaexpress.bff.eventos.PublicadorKafka;
import cl.farmaexpress.bff.eventos.VentaRegistrada;

/** El productor Kafka del BFF: tópico, clave y tolerancia a un Kafka caído. */
class PublicadorKafkaTests {

	private static final VentaRegistrada VENTA = new VentaRegistrada("ev-1", VentaRegistrada.VERSION, 42L, Instant.now(),
			"RETIRO", 1L, "FarmaExpress Providencia", "WEBPAY", 0, 3980,
			List.of(new VentaRegistrada.Item(1L, "Paracetamol 500 mg", 2, 1990)));

	@SuppressWarnings("unchecked")
	private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);

	@Test
	void publicaLaVentaEnSuTopicoConElPedidoComoClave() {
		when(kafka.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<SendResult<String, Object>>());

		new PublicadorKafka(kafka, true).ventaRegistrada(VENTA);

		verify(kafka).send(KafkaConfig.TOPICO_VENTAS, "42", VENTA);
	}

	@Test
	void deshabilitadoNoPublica() {
		new PublicadorKafka(kafka, false).ventaRegistrada(VENTA);

		verify(kafka, never()).send(anyString(), anyString(), any());
	}

	@Test
	void unKafkaCaidoNoInterrumpeElPedido() {
		when(kafka.send(eq(KafkaConfig.TOPICO_VENTAS), anyString(), any())).thenThrow(new TimeoutException("sin brokers"));

		assertThatCode(() -> new PublicadorKafka(kafka, true).ventaRegistrada(VENTA)).doesNotThrowAnyException();
	}
}
