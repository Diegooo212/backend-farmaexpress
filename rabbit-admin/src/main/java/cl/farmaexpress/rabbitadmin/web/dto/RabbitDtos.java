package cl.farmaexpress.rabbitadmin.web.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Entradas y salidas de la API del administrador. Las entradas se validan antes de tocar RabbitMQ. */
public final class RabbitDtos {

	private RabbitDtos() {
	}

	/** Letras, números, punto, guion y guion bajo; sin espacios. Ej: "tickets.soporte". */
	public static final String NOMBRE = "^[a-zA-Z0-9][a-zA-Z0-9._-]*$";
	private static final String NOMBRE_MSG = "Usa solo letras, números, punto, guion o guion bajo, sin espacios.";

	/** Palabras separadas por punto; en topic se permiten los comodines * y #. Ej: "ticket.*.creado". */
	public static final String ROUTING_KEY = "^$|^([a-zA-Z0-9_-]+|\\*|#)(\\.([a-zA-Z0-9_-]+|\\*|#))*$";

	public enum TipoCola {
		/** Cola clásica: vive en un nodo. */
		CLASSIC,
		/** Cola de quórum: replicada en los nodos del clúster (sobrevive si cae uno). */
		QUORUM
	}

	public enum TipoExchange {
		DIRECT, TOPIC, FANOUT, HEADERS;

		public String valor() {
			return name().toLowerCase();
		}
	}

	public record ColaRequest(
			@NotBlank(message = "El nombre de la cola es obligatorio.")
			@Size(min = 3, max = 120, message = "El nombre debe tener entre 3 y 120 caracteres.")
			@Pattern(regexp = NOMBRE, message = NOMBRE_MSG) String nombre,
			TipoCola tipo,
			Boolean durable,
			/** Crea también "<nombre>.dlq" y le envía los mensajes rechazados o vencidos. */
			Boolean conDlq,
			@Positive(message = "El TTL debe ser mayor que cero.")
			@Max(value = 604_800_000, message = "El TTL no puede superar 7 días (604800000 ms).") Long ttlMs,
			@Positive(message = "El largo máximo debe ser mayor que cero.")
			@Max(value = 1_000_000, message = "El largo máximo no puede superar 1.000.000 mensajes.") Integer maxMensajes) {

		public TipoCola tipoOPorDefecto() {
			return tipo == null ? TipoCola.CLASSIC : tipo;
		}

		public boolean esDurable() {
			return durable == null || durable;
		}

		public boolean llevaDlq() {
			return conDlq == null || conDlq;
		}
	}

	public record ExchangeRequest(
			@NotBlank(message = "El nombre del exchange es obligatorio.")
			@Size(min = 3, max = 120, message = "El nombre debe tener entre 3 y 120 caracteres.")
			@Pattern(regexp = NOMBRE, message = NOMBRE_MSG) String nombre,
			@NotNull(message = "Indica el tipo: DIRECT, TOPIC, FANOUT o HEADERS.") TipoExchange tipo,
			Boolean durable) {

		public boolean esDurable() {
			return durable == null || durable;
		}
	}

	public record BindingRequest(
			@NotBlank(message = "Indica el exchange de origen.") @Pattern(regexp = NOMBRE, message = NOMBRE_MSG) String exchange,
			@NotBlank(message = "Indica la cola de destino.") @Pattern(regexp = NOMBRE, message = NOMBRE_MSG) String cola,
			@Size(max = 255, message = "La routing key no puede superar 255 caracteres.")
			@Pattern(regexp = ROUTING_KEY, message = "La routing key son palabras separadas por punto (en topic se permiten * y #).")
			String routingKey) {

		public String routingKeyONada() {
			return routingKey == null ? "" : routingKey;
		}
	}

	public record ColaResponse(String nombre, String tipo, boolean durable, long mensajes, int consumidores,
			String dlq, boolean protegida) {
	}

	public record ExchangeResponse(String nombre, String tipo, boolean durable, boolean protegido) {
	}

	public record BindingResponse(String exchange, String cola, String routingKey) {
	}

	public record ColaCreada(ColaResponse cola, List<String> recursosCreados, Map<String, Object> argumentos) {
	}
}
