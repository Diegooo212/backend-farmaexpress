package cl.farmaexpress.kafkaadmin.web.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Entradas y salidas del administrador de Kafka. */
public final class KafkaDtos {

	private KafkaDtos() {
	}

	/** Caracteres que Kafka acepta en el nombre de un tópico o de un grupo. */
	public static final String NOMBRE = "^[a-zA-Z0-9._-]+$";
	private static final String NOMBRE_MSG = "Usa solo letras, números, punto, guion o guion bajo, sin espacios.";

	// ---------------------------------------------------------------- Clúster

	public record ClusterResponse(String clusterId, Integer controlador, List<BrokerResponse> brokers) {
	}

	public record BrokerResponse(int id, String host, int puerto, String rack, boolean controlador) {
	}

	// ---------------------------------------------------------------- Tópicos

	/**
	 * @param mensajes mensajes que hoy guarda el tópico (offset final menos inicial de cada partición)
	 * @param config valores vigentes de las configuraciones más relevantes
	 */
	public record TopicoResumen(String nombre, int particiones, int replicas, boolean interno, boolean protegido,
			long mensajes, Map<String, String> config) {
	}

	public record TopicoDetalle(String nombre, boolean interno, boolean protegido, int replicas,
			List<ParticionResponse> particiones, List<ConfigResponse> config, long mensajes) {
	}

	/**
	 * @param isr réplicas sincronizadas (in-sync replicas): las que tienen todos los mensajes del líder
	 * @param subReplicada hay réplicas fuera del ISR (por ejemplo, un broker caído)
	 */
	public record ParticionResponse(int particion, Integer lider, List<Integer> replicas, List<Integer> isr,
			long offsetInicial, long offsetFinal, boolean subReplicada) {
	}

	/** @param personalizada definida en el tópico (si no, hereda el valor del broker) */
	public record ConfigResponse(String nombre, String valor, boolean personalizada) {
	}

	/**
	 * @param config configuraciones del tópico (retention.ms, cleanup.policy, min.insync.replicas...)
	 */
	public record CrearTopicoRequest(
			@NotBlank(message = "El nombre del tópico es obligatorio.")
			@Size(min = 3, max = 200, message = "El nombre debe tener entre 3 y 200 caracteres.")
			@Pattern(regexp = NOMBRE, message = NOMBRE_MSG) String nombre,
			@NotNull(message = "Indica el número de particiones.")
			@Min(value = 1, message = "Un tópico necesita al menos 1 partición.")
			@Max(value = 12, message = "Usa como máximo 12 particiones.") Integer particiones,
			@NotNull(message = "Indica el factor de replicación.")
			@Min(value = 1, message = "El factor de replicación mínimo es 1.")
			@Max(value = 3, message = "El factor de replicación no puede superar los 3 brokers del clúster.") Integer replicas,
			Map<String, String> config) {

		public Map<String, String> configONada() {
			return config == null ? Map.of() : config;
		}
	}

	/** Un valor vacío o nulo devuelve la configuración a su valor por defecto. */
	public record CambiarConfigRequest(@NotEmpty(message = "Indica al menos una configuración.") Map<String, String> config) {
	}

	public record ParticionesRequest(
			@NotNull(message = "Indica el nuevo total de particiones.")
			@Min(value = 2, message = "El nuevo total debe ser al menos 2.")
			@Max(value = 12, message = "Usa como máximo 12 particiones.") Integer total) {
	}

	// ---------------------------------------------------------------- Consumer groups

	/**
	 * @param lagTotal mensajes que el grupo aún no procesa, sumando todas sus particiones
	 */
	public record GrupoResumen(String grupo, String estado, int miembros, List<String> topicos, long lagTotal) {
	}

	public record GrupoDetalle(String grupo, String estado, String asignador, Integer coordinador,
			List<MiembroResponse> miembros, List<OffsetResponse> offsets, long lagTotal) {
	}

	/** @param particiones particiones asignadas, como "tópico-partición" */
	public record MiembroResponse(String consumerId, String clientId, String host, List<String> particiones) {
	}

	/**
	 * @param offsetConsumido siguiente offset que el grupo va a leer (lo último que confirmó)
	 * @param offsetFinal offset del próximo mensaje que se escriba en la partición
	 * @param lag mensajes pendientes = offsetFinal - offsetConsumido
	 * @param consumidor consumidor (client id) que tiene asignada la partición, o null si nadie la lee
	 */
	public record OffsetResponse(String topico, int particion, long offsetConsumido, long offsetFinal, long lag,
			String consumidor) {
	}

	public enum Posicion {
		/** Volver a procesar todo lo que el tópico conserva. */
		EARLIEST,
		/** Saltar lo pendiente y leer solo lo que llegue desde ahora. */
		LATEST
	}

	public record ReiniciarOffsetsRequest(
			@NotBlank(message = "Indica el tópico.") @Pattern(regexp = NOMBRE, message = NOMBRE_MSG) String topico,
			@NotNull(message = "Indica la posición: EARLIEST o LATEST.") Posicion posicion) {
	}
}
