package cl.farmaexpress.notifications;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.QueryTimeoutException;

import com.rabbitmq.client.Channel;

import cl.farmaexpress.notifications.eventos.ConfirmacionMensajes;

/** Decisiones de confirmación de un consumidor: ACK, NACK con reintento o NACK a la DLQ. */
class ConfirmacionMensajesTests {

	private final ConfirmacionMensajes confirmacion = new ConfirmacionMensajes();
	private final Channel canal = mock(Channel.class);

	private static Message mensaje(long tag, boolean reentregado) {
		MessageProperties props = new MessageProperties();
		props.setDeliveryTag(tag);
		props.setRedelivered(reentregado);
		props.setConsumerQueue("notifications.recetas");
		props.setReceivedRoutingKey("receta.estado-cambiado");
		return new Message("{\"eventoId\":\"ev-1\"}".getBytes(StandardCharsets.UTF_8), props);
	}

	@Test
	void siSeProcesaBienSeConfirmaConAck() throws Exception {
		confirmacion.procesar(mensaje(7, false), canal, () -> {
		});
		verify(canal).basicAck(7, false);
		verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	@Test
	void unErrorRecuperableSeReintentaUnaVezDevolviendoloALaCola() throws Exception {
		confirmacion.procesar(mensaje(8, false), canal, () -> {
			throw new QueryTimeoutException("la base de datos no respondió");
		});
		verify(canal).basicNack(8, false, true);
		verify(canal, never()).basicAck(anyLong(), anyBoolean());
	}

	@Test
	void siVuelveAFallarAlReintentarVaALaDlq() throws Exception {
		confirmacion.procesar(mensaje(9, true), canal, () -> {
			throw new QueryTimeoutException("la base de datos sigue sin responder");
		});
		verify(canal).basicNack(9, false, false);
	}

	@Test
	void unErrorNoRecuperableVaDirectoALaDlqSinReintentar() throws Exception {
		confirmacion.procesar(mensaje(10, false), canal, () -> {
			throw new IllegalArgumentException("evento sin cuentaId");
		});
		verify(canal).basicNack(10, false, false);
		verify(canal, never()).basicNack(10, false, true);
	}
}
