package cl.farmaexpress.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import cl.farmaexpress.inventory.config.KafkaConfig;
import cl.farmaexpress.inventory.eventos.Eventos.StockCambiado;
import cl.farmaexpress.inventory.eventos.Eventos.StockProducto;
import cl.farmaexpress.inventory.eventos.PublicadorKafka;
import cl.farmaexpress.inventory.eventos.StockActualizado;

/** El productor Kafka de inventory: un mensaje por producto, con el producto como clave. */
class PublicadorKafkaTests {

	@SuppressWarnings("unchecked")
	private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);

	private static final StockCambiado CAMBIO = new StockCambiado("ev-1",
			List.of(new StockProducto(5L, 12, 40), new StockProducto(9L, 0, 0)), Instant.parse("2026-11-16T12:00:00Z"));

	@Test
	void publicaUnMensajePorProductoConSuIdComoClave() {
		when(kafka.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<SendResult<String, Object>>());

		new PublicadorKafka(kafka, true).stockCambiado(CAMBIO);

		ArgumentCaptor<Object> eventos = ArgumentCaptor.forClass(Object.class);
		verify(kafka).send(eq(KafkaConfig.TOPICO_STOCK), eq("5"), eventos.capture());
		verify(kafka).send(eq(KafkaConfig.TOPICO_STOCK), eq("9"), eventos.capture());
		assertThat(eventos.getAllValues()).first().isInstanceOfSatisfying(StockActualizado.class, e -> {
			assertThat(e.medicamentoId()).isEqualTo(5L);
			assertThat(e.stockPorPedido()).isEqualTo(12);
			assertThat(e.stockTotal()).isEqualTo(40);
			assertThat(e.version()).isEqualTo(StockActualizado.VERSION);
			assertThat(e.fecha()).isEqualTo(CAMBIO.fecha());
		});
	}

	@Test
	void deshabilitadoNoPublica() {
		new PublicadorKafka(kafka, false).stockCambiado(CAMBIO);

		verify(kafka, never()).send(anyString(), anyString(), any());
	}

	@Test
	void unKafkaCaidoNoInterrumpeElInventario() {
		when(kafka.send(anyString(), anyString(), any())).thenThrow(new TimeoutException("sin brokers"));

		assertThatCode(() -> new PublicadorKafka(kafka, true).stockCambiado(CAMBIO)).doesNotThrowAnyException();
		verify(kafka, times(2)).send(anyString(), anyString(), any());
	}
}
