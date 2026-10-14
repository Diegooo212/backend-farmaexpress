package cl.farmaexpress.notifications.eventos;

import java.io.IOException;
import java.net.ConnectException;
import java.sql.SQLTransientException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.client.ResourceAccessException;

import com.rabbitmq.client.Channel;

/**
 * Confirmación manual (ACK) de los mensajes de RabbitMQ ({@code acknowledge-mode=manual}).
 * Cada consumidor ({@code @RabbitListener}) delega aquí el procesamiento y esta clase decide:
 *
 * <pre>
 *  procesó bien ─────────────────────────────────────────────► basicAck            (se borra de la cola)
 *  error RECUPERABLE (BD o red caída un momento), 1ª vez ────► basicNack requeue   (se reintenta)
 *  error RECUPERABLE por 2ª vez ─────────────────────────────► basicNack sin requeue → DLQ + log ERROR
 *  error NO RECUPERABLE (dato inválido, regla de negocio) ───► basicNack sin requeue → DLQ + log ERROR
 * </pre>
 *
 * El "sin requeue" hace que RabbitMQ envíe el mensaje al dead-letter exchange configurado en la
 * cola (farmaexpress.eventos.dlx), que lo deja en la DLQ del servicio para revisarlo. Ningún
 * mensaje se pierde ni queda reintentándose para siempre.
 */
@Component
public class ConfirmacionMensajes {

	private static final Logger log = LoggerFactory.getLogger(ConfirmacionMensajes.class);

	/** Trabajo del consumidor (puede lanzar cualquier excepción). */
	@FunctionalInterface
	public interface Trabajo {
		void ejecutar() throws Exception;
	}

	public void procesar(Message mensaje, Channel canal, Trabajo trabajo) {
		MessageProperties props = mensaje.getMessageProperties();
		long tag = props.getDeliveryTag();
		String origen = props.getConsumerQueue() + " (routing key " + props.getReceivedRoutingKey() + ")";
		try {
			trabajo.ejecutar();
			canal.basicAck(tag, false);
		}
		catch (Exception error) {
			boolean recuperable = esRecuperable(error);
			boolean reintentar = recuperable && !Boolean.TRUE.equals(props.isRedelivered());
			try {
				if (reintentar) {
					log.warn("Error recuperable en {}: {}. Se devuelve a la cola para reintentar una vez.", origen,
							error.toString());
					canal.basicNack(tag, false, true);
				}
				else {
					log.error("Mensaje enviado a la DLQ desde {} ({}): {}. Cuerpo: {}", origen,
							recuperable ? "falló de nuevo al reintentar" : "error no recuperable", error.toString(),
							new String(mensaje.getBody(), java.nio.charset.StandardCharsets.UTF_8));
					canal.basicNack(tag, false, false);
				}
			}
			catch (IOException e) {
				// El canal se cerró: RabbitMQ volverá a entregar el mensaje (no se confirmó).
				log.error("No se pudo confirmar el mensaje de {}: {}", origen, e.toString());
			}
		}
	}

	/** Fallas transitorias de infraestructura: vale la pena reintentar. Todo lo demás va a la DLQ. */
	static boolean esRecuperable(Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause()) {
			if (t instanceof TransientDataAccessException || t instanceof CannotCreateTransactionException
					|| t instanceof ResourceAccessException || t instanceof SQLTransientException
					|| t instanceof ConnectException) {
				return true;
			}
		}
		return false;
	}
}
