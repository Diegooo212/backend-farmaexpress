package cl.farmaexpress.kafkaadmin.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Configuraciones de tópico que se pueden definir desde la API, con la regla que valida cada valor.
 * Una lista cerrada evita errores de tipeo y valores que pondrían en riesgo el clúster.
 */
public final class ConfiguracionesTopico {

	private ConfiguracionesTopico() {
	}

	private record Regla(Predicate<String> valida, String ayuda) {
	}

	/** En el orden en que se muestran. */
	private static final Map<String, Regla> REGLAS = new LinkedHashMap<>();

	static {
		REGLAS.put("cleanup.policy", new Regla(v -> Set.of("delete", "compact", "compact,delete", "delete,compact").contains(v),
				"delete (borra por antigüedad o tamaño), compact (conserva el último valor de cada clave) o compact,delete"));
		REGLAS.put("retention.ms", new Regla(v -> esLong(v, -1, Long.MAX_VALUE) && (Long.parseLong(v) == -1 || Long.parseLong(v) >= 60_000),
				"milisegundos que se conservan los mensajes: -1 (sin límite) o al menos 60000 (1 minuto)"));
		REGLAS.put("retention.bytes", new Regla(v -> esLong(v, -1, Long.MAX_VALUE),
				"bytes máximos por partición antes de borrar lo más antiguo; -1 sin límite"));
		REGLAS.put("min.insync.replicas", new Regla(v -> esLong(v, 1, 3),
				"réplicas que deben confirmar una escritura con acks=all: entre 1 y 3"));
		REGLAS.put("segment.ms", new Regla(v -> esLong(v, 60_000, Long.MAX_VALUE),
				"cada cuánto se cierra un segmento del log (al menos 60000 ms); la retención y la compactación actúan sobre segmentos cerrados"));
		REGLAS.put("min.compaction.lag.ms", new Regla(v -> esLong(v, 0, Long.MAX_VALUE),
				"tiempo mínimo que un mensaje queda sin compactar, en ms"));
		REGLAS.put("delete.retention.ms", new Regla(v -> esLong(v, 0, Long.MAX_VALUE),
				"tiempo que se conservan las lápidas (mensajes nulos) en un tópico compactado, en ms"));
		REGLAS.put("min.cleanable.dirty.ratio", new Regla(v -> esDecimal(v, 0.0, 1.0),
				"proporción del log sin compactar que dispara la compactación: entre 0 y 1"));
		REGLAS.put("max.message.bytes", new Regla(v -> esLong(v, 1024, 10_485_760),
				"tamaño máximo de un mensaje: entre 1024 y 10485760 bytes"));
		REGLAS.put("compression.type", new Regla(v -> Set.of("producer", "uncompressed", "gzip", "snappy", "lz4", "zstd").contains(v),
				"producer, uncompressed, gzip, snappy, lz4 o zstd"));
	}

	/** Nombres de las configuraciones administrables, en orden. */
	public static List<String> nombres() {
		return List.copyOf(REGLAS.keySet());
	}

	/** Configuraciones que se muestran en el listado de tópicos. */
	public static final List<String> RESUMEN = List.of("cleanup.policy", "retention.ms", "min.insync.replicas");

	/**
	 * Valida cada configuración. {@code admiteVacio}: un valor vacío significa "volver al valor por defecto".
	 * @throws KafkaAdminException 400 con el detalle de la primera configuración inválida
	 */
	public static void validar(Map<String, String> config, boolean admiteVacio) {
		config.forEach((nombre, valor) -> {
			Regla regla = REGLAS.get(nombre);
			if (regla == null) {
				throw KafkaAdminException.invalido("La configuración \"" + nombre + "\" no se puede administrar desde aquí. "
						+ "Permitidas: " + String.join(", ", REGLAS.keySet()) + ".");
			}
			if (valor == null || valor.isBlank()) {
				if (!admiteVacio) {
					throw KafkaAdminException.invalido("Indica un valor para \"" + nombre + "\".");
				}
				return;
			}
			if (!regla.valida().test(valor.trim())) {
				throw KafkaAdminException.invalido("Valor inválido para \"" + nombre + "\": " + regla.ayuda() + ".");
			}
		});
	}

	private static boolean esLong(String v, long min, long max) {
		try {
			long n = Long.parseLong(v);
			return n >= min && n <= max;
		}
		catch (NumberFormatException ex) {
			return false;
		}
	}

	private static boolean esDecimal(String v, double min, double max) {
		try {
			double n = Double.parseDouble(v);
			return n >= min && n <= max;
		}
		catch (NumberFormatException ex) {
			return false;
		}
	}
}
