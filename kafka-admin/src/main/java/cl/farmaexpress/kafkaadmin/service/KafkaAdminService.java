package cl.farmaexpress.kafkaadmin.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.GroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.GroupNotEmptyException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.PolicyViolationException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import cl.farmaexpress.kafkaadmin.config.KafkaAdminProperties;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.BrokerResponse;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.ClusterResponse;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.ConfigResponse;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.CrearTopicoRequest;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.GrupoDetalle;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.GrupoResumen;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.MiembroResponse;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.OffsetResponse;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.ParticionResponse;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.Posicion;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.TopicoDetalle;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.TopicoResumen;

/**
 * Administración del clúster Kafka con el AdminClient oficial: brokers, tópicos (crear, modificar
 * configuraciones, agregar particiones, eliminar) y consumer groups (estado, offsets, lag y reinicio de
 * offsets). Los tópicos del sistema no se pueden eliminar ni cambiar de particiones.
 */
@Service
public class KafkaAdminService {

	private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);

	private final AdminClient admin;
	private final KafkaAdminProperties props;

	public KafkaAdminService(AdminClient admin, KafkaAdminProperties props) {
		this.admin = admin;
		this.props = props;
	}

	// ---------------------------------------------------------------- Clúster

	public ClusterResponse cluster() {
		DescribeClusterResult cluster = admin.describeCluster();
		Node controlador = esperar(cluster.controller(), "consultar el clúster");
		Integer idControlador = controlador == null ? null : controlador.id();
		List<BrokerResponse> brokers = esperar(cluster.nodes(), "consultar el clúster").stream()
			.sorted(Comparator.comparingInt(Node::id))
			.map(n -> new BrokerResponse(n.id(), n.host(), n.port(), n.rack(), idControlador != null && n.id() == idControlador))
			.toList();
		return new ClusterResponse(esperar(cluster.clusterId(), "consultar el clúster"), idControlador, brokers);
	}

	// ---------------------------------------------------------------- Tópicos

	public List<TopicoResumen> listarTopicos(boolean incluirInternos) {
		Set<String> nombres = esperar(admin.listTopics(new ListTopicsOptions().listInternal(incluirInternos)).names(),
				"listar los tópicos");
		if (nombres.isEmpty()) {
			return List.of();
		}
		Map<String, TopicDescription> descripciones = describir(nombres);
		Map<String, Config> configs = configs(nombres);
		Map<TopicPartition, long[]> offsets = offsets(descripciones.values());
		return descripciones.values().stream()
			.sorted(Comparator.comparing(TopicDescription::name))
			.map(d -> {
				Map<String, String> resumen = new LinkedHashMap<>();
				Config config = configs.get(d.name());
				ConfiguracionesTopico.RESUMEN.forEach(c -> {
					ConfigEntry entrada = config == null ? null : config.get(c);
					resumen.put(c, entrada == null ? null : entrada.value());
				});
				return new TopicoResumen(d.name(), d.partitions().size(), replicas(d), d.isInternal(), esProtegido(d.name()),
						mensajes(d, offsets), resumen);
			})
			.toList();
	}

	public TopicoDetalle topico(String nombre) {
		TopicDescription d = describir(Set.of(nombre)).get(nombre);
		Map<TopicPartition, long[]> offsets = offsets(List.of(d));
		List<ParticionResponse> particiones = d.partitions().stream()
			.sorted(Comparator.comparingInt(TopicPartitionInfo::partition))
			.map(p -> {
				long[] o = offsets.getOrDefault(new TopicPartition(nombre, p.partition()), new long[] { 0, 0 });
				List<Integer> replicas = p.replicas().stream().map(Node::id).toList();
				List<Integer> isr = p.isr().stream().map(Node::id).toList();
				return new ParticionResponse(p.partition(), p.leader() == null ? null : p.leader().id(), replicas, isr, o[0], o[1],
						isr.size() < replicas.size());
			})
			.toList();
		Config config = configs(Set.of(nombre)).get(nombre);
		List<ConfigResponse> configuraciones = ConfiguracionesTopico.nombres().stream()
			.map(c -> {
				ConfigEntry e = config == null ? null : config.get(c);
				return new ConfigResponse(c, e == null ? null : e.value(),
						e != null && e.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG);
			})
			.toList();
		return new TopicoDetalle(nombre, d.isInternal(), esProtegido(nombre), replicas(d), particiones, configuraciones,
				mensajes(d, offsets));
	}

	public TopicoDetalle crearTopico(CrearTopicoRequest req) {
		int brokers = esperar(admin.describeCluster().nodes(), "consultar el clúster").size();
		if (req.replicas() > brokers) {
			throw KafkaAdminException.invalido("El factor de replicación (" + req.replicas()
					+ ") no puede superar los brokers disponibles (" + brokers + ").");
		}
		Map<String, String> config = limpiar(req.configONada());
		ConfiguracionesTopico.validar(config, false);
		validarIsr(config.get("min.insync.replicas"), req.replicas());
		NewTopic topico = new NewTopic(req.nombre(), req.particiones(), req.replicas().shortValue()).configs(config);
		esperar(admin.createTopics(List.of(topico)).all(), "crear el tópico");
		log.info("Tópico creado: {} ({} particiones, {} réplicas, config {})", req.nombre(), req.particiones(), req.replicas(),
				config);
		return topico(req.nombre());
	}

	/** Cambia (o devuelve a su valor por defecto, si viene vacía) cada configuración indicada. */
	public TopicoDetalle cambiarConfig(String nombre, Map<String, String> cambios) {
		if (KafkaAdminProperties.esInterno(nombre)) {
			throw KafkaAdminException.conflicto("\"" + nombre + "\" es un tópico interno de Kafka y no se configura a mano.");
		}
		ConfiguracionesTopico.validar(cambios, true);
		TopicDescription d = describir(Set.of(nombre)).get(nombre);
		String isr = cambios.get("min.insync.replicas");
		if (isr != null && !isr.isBlank()) {
			validarIsr(isr.trim(), replicas(d));
		}
		ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
		List<AlterConfigOp> operaciones = cambios.entrySet().stream()
			.map(e -> e.getValue() == null || e.getValue().isBlank()
					? new AlterConfigOp(new ConfigEntry(e.getKey(), null), AlterConfigOp.OpType.DELETE)
					: new AlterConfigOp(new ConfigEntry(e.getKey(), e.getValue().trim()), AlterConfigOp.OpType.SET))
			.toList();
		esperar(admin.incrementalAlterConfigs(Map.of(recurso, operaciones)).all(), "cambiar la configuración");
		log.info("Configuración del tópico {} cambiada: {}", nombre, cambios);
		return topico(nombre);
	}

	/** Kafka solo permite aumentar las particiones; en un tópico con claves cambia la partición de cada clave. */
	public TopicoDetalle agregarParticiones(String nombre, int total) {
		if (esProtegido(nombre)) {
			throw KafkaAdminException.conflicto("\"" + nombre + "\" es un tópico del sistema: cambiar sus particiones movería "
					+ "cada clave a otra partición y rompería el orden de sus eventos.");
		}
		TopicDescription d = describir(Set.of(nombre)).get(nombre);
		if (total <= d.partitions().size()) {
			throw KafkaAdminException.invalido("El tópico ya tiene " + d.partitions().size()
					+ " particiones: Kafka solo permite aumentarlas.");
		}
		esperar(admin.createPartitions(Map.of(nombre, NewPartitions.increaseTo(total))).all(), "agregar particiones");
		log.info("Tópico {}: particiones aumentadas de {} a {}", nombre, d.partitions().size(), total);
		return topico(nombre);
	}

	public void eliminarTopico(String nombre) {
		if (esProtegido(nombre)) {
			throw KafkaAdminException.conflicto("\"" + nombre + "\" es parte del sistema (lo usan los microservicios de "
					+ "FarmaExpress) y no se puede eliminar.");
		}
		describir(Set.of(nombre));
		esperar(admin.deleteTopics(List.of(nombre)).all(), "eliminar el tópico");
		log.info("Tópico eliminado: {}", nombre);
	}

	// ---------------------------------------------------------------- Consumer groups

	public List<GrupoResumen> listarGrupos() {
		List<String> ids = idsDeGrupos();
		if (ids.isEmpty()) {
			return List.of();
		}
		Map<String, ConsumerGroupDescription> descripciones = esperar(admin.describeConsumerGroups(ids).all(),
				"consultar los consumer groups");
		return ids.stream()
			.sorted()
			.map(id -> {
				ConsumerGroupDescription d = descripciones.get(id);
				List<OffsetResponse> offsets = offsetsDelGrupo(id, d);
				List<String> topicos = offsets.stream().map(OffsetResponse::topico).collect(Collectors.toCollection(TreeSet::new))
					.stream().toList();
				return new GrupoResumen(id, estado(d), d.members().size(), topicos,
						offsets.stream().mapToLong(OffsetResponse::lag).sum());
			})
			.toList();
	}

	public GrupoDetalle grupo(String grupo) {
		ConsumerGroupDescription d = describirGrupo(grupo);
		List<MiembroResponse> miembros = d.members().stream()
			.sorted(Comparator.comparing(MemberDescription::clientId).thenComparing(MemberDescription::consumerId))
			.map(m -> new MiembroResponse(m.consumerId(), m.clientId(), m.host(), m.assignment().topicPartitions().stream()
				.sorted(Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition))
				.map(tp -> tp.topic() + "-" + tp.partition())
				.toList()))
			.toList();
		List<OffsetResponse> offsets = offsetsDelGrupo(grupo, d);
		return new GrupoDetalle(grupo, estado(d), d.partitionAssignor(), d.coordinator() == null ? null : d.coordinator().id(),
				miembros, offsets, offsets.stream().mapToLong(OffsetResponse::lag).sum());
	}

	/**
	 * Mueve los offsets del grupo en un tópico al inicio (reprocesar lo que el tópico conserva) o al final
	 * (saltar lo pendiente). Kafka solo lo permite si el grupo no tiene consumidores activos.
	 */
	public GrupoDetalle reiniciarOffsets(String grupo, String topico, Posicion posicion) {
		ConsumerGroupDescription d = describirGrupo(grupo);
		if (!d.members().isEmpty()) {
			throw KafkaAdminException.conflicto("El grupo \"" + grupo + "\" tiene " + d.members().size()
					+ " consumidores activos: detén el microservicio que lo usa antes de mover sus offsets.");
		}
		TopicDescription t = describir(Set.of(topico)).get(topico);
		OffsetSpec spec = posicion == Posicion.EARLIEST ? OffsetSpec.earliest() : OffsetSpec.latest();
		Map<TopicPartition, OffsetSpec> pedido = t.partitions().stream()
			.collect(Collectors.toMap(p -> new TopicPartition(topico, p.partition()), p -> spec));
		Map<TopicPartition, ListOffsetsResultInfo> posiciones = esperar(admin.listOffsets(pedido).all(), "consultar los offsets");
		Map<TopicPartition, OffsetAndMetadata> nuevos = posiciones.entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getKey, e -> new OffsetAndMetadata(e.getValue().offset())));
		esperar(admin.alterConsumerGroupOffsets(grupo, nuevos).all(), "mover los offsets");
		log.info("Offsets del grupo {} en {} movidos a {}: {}", grupo, topico, posicion, nuevos);
		return grupo(grupo);
	}

	public void eliminarGrupo(String grupo) {
		describirGrupo(grupo);
		esperar(admin.deleteConsumerGroups(List.of(grupo)).all(), "eliminar el grupo");
		log.info("Consumer group eliminado: {}", grupo);
	}

	// ---------------------------------------------------------------- Apoyo

	private boolean esProtegido(String topico) {
		return KafkaAdminProperties.esInterno(topico) || props.esProtegido(topico);
	}

	private List<String> idsDeGrupos() {
		Collection<GroupListing> grupos = esperar(admin.listGroups().all(), "listar los consumer groups");
		// Solo consumer groups (protocolo "consumer"); se descartan los de Kafka Connect, share groups, etc.
		return grupos.stream()
			.filter(g -> "consumer".equals(g.protocol()) || g.type().map(t -> t.toString().equalsIgnoreCase("consumer")).orElse(false))
			.map(GroupListing::groupId)
			.toList();
	}

	private ConsumerGroupDescription describirGrupo(String grupo) {
		ConsumerGroupDescription d = esperar(admin.describeConsumerGroups(List.of(grupo)).all(), "consultar el grupo").get(grupo);
		if (d == null || d.groupState() == GroupState.DEAD) {
			throw KafkaAdminException.noExiste("el consumer group", grupo);
		}
		return d;
	}

	private static String estado(ConsumerGroupDescription d) {
		return d.groupState() == null ? "UNKNOWN" : d.groupState().name();
	}

	/** Offset consumido, offset final y lag de cada partición donde el grupo confirmó offsets o tiene asignación. */
	private List<OffsetResponse> offsetsDelGrupo(String grupo, ConsumerGroupDescription d) {
		Map<TopicPartition, OffsetAndMetadata> consumidos = esperar(
				admin.listConsumerGroupOffsets(grupo).partitionsToOffsetAndMetadata(), "consultar los offsets del grupo");
		Map<TopicPartition, String> asignadas = new HashMap<>();
		for (MemberDescription m : d.members()) {
			m.assignment().topicPartitions().forEach(tp -> asignadas.put(tp, m.clientId()));
		}
		Set<TopicPartition> particiones = new java.util.HashSet<>(consumidos.keySet());
		particiones.addAll(asignadas.keySet());
		if (particiones.isEmpty()) {
			return List.of();
		}
		Map<TopicPartition, ListOffsetsResultInfo> finales = esperar(
				admin.listOffsets(particiones.stream().collect(Collectors.toMap(Function.identity(), tp -> OffsetSpec.latest()))).all(),
				"consultar los offsets");
		return particiones.stream()
			.sorted(Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition))
			.map(tp -> {
				OffsetAndMetadata om = consumidos.get(tp);
				long fin = finales.containsKey(tp) ? finales.get(tp).offset() : 0;
				long consumido = om == null ? 0 : om.offset();
				return new OffsetResponse(tp.topic(), tp.partition(), consumido, fin, Math.max(0, fin - consumido), asignadas.get(tp));
			})
			.toList();
	}

	private Map<String, TopicDescription> describir(Set<String> nombres) {
		return esperar(admin.describeTopics(nombres).allTopicNames(), "consultar el tópico");
	}

	private Map<String, Config> configs(Set<String> nombres) {
		List<ConfigResource> recursos = nombres.stream().map(n -> new ConfigResource(ConfigResource.Type.TOPIC, n)).toList();
		return esperar(admin.describeConfigs(recursos).all(), "consultar la configuración").entrySet().stream()
			.collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
	}

	/** Offset inicial y final de cada partición de los tópicos indicados. */
	private Map<TopicPartition, long[]> offsets(Collection<TopicDescription> topicos) {
		List<TopicPartition> particiones = new ArrayList<>();
		topicos.forEach(t -> t.partitions().forEach(p -> particiones.add(new TopicPartition(t.name(), p.partition()))));
		if (particiones.isEmpty()) {
			return Map.of();
		}
		Map<TopicPartition, ListOffsetsResultInfo> inicio = esperar(
				admin.listOffsets(particiones.stream().collect(Collectors.toMap(Function.identity(), tp -> OffsetSpec.earliest()))).all(),
				"consultar los offsets");
		Map<TopicPartition, ListOffsetsResultInfo> fin = esperar(
				admin.listOffsets(particiones.stream().collect(Collectors.toMap(Function.identity(), tp -> OffsetSpec.latest()))).all(),
				"consultar los offsets");
		Map<TopicPartition, long[]> resultado = new HashMap<>();
		particiones.forEach(tp -> resultado.put(tp, new long[] { inicio.containsKey(tp) ? inicio.get(tp).offset() : 0,
				fin.containsKey(tp) ? fin.get(tp).offset() : 0 }));
		return resultado;
	}

	/** En un tópico compactado no todos los offsets tienen mensaje: es una aproximación (cota superior). */
	private static long mensajes(TopicDescription d, Map<TopicPartition, long[]> offsets) {
		return d.partitions().stream()
			.mapToLong(p -> {
				long[] o = offsets.get(new TopicPartition(d.name(), p.partition()));
				return o == null ? 0 : Math.max(0, o[1] - o[0]);
			})
			.sum();
	}

	private static int replicas(TopicDescription d) {
		return d.partitions().isEmpty() ? 0 : d.partitions().get(0).replicas().size();
	}

	private static void validarIsr(String valor, int replicas) {
		if (valor != null && Integer.parseInt(valor) > replicas) {
			throw KafkaAdminException.invalido("min.insync.replicas (" + valor + ") no puede ser mayor que el factor de "
					+ "replicación (" + replicas + "): ninguna escritura con acks=all podría confirmarse.");
		}
	}

	private static Map<String, String> limpiar(Map<String, String> config) {
		Map<String, String> limpio = new LinkedHashMap<>();
		config.forEach((k, v) -> {
			if (v != null && !v.isBlank()) {
				limpio.put(k.trim(), v.trim());
			}
		});
		return limpio;
	}

	/** Espera el resultado de Kafka con un tiempo máximo y traduce sus errores a respuestas HTTP claras. */
	private <T> T esperar(KafkaFuture<T> futuro, String accion) {
		try {
			return futuro.get(props.timeout().toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (ExecutionException ex) {
			throw traducir(ex.getCause(), accion);
		}
		catch (java.util.concurrent.TimeoutException ex) {
			throw new KafkaAdminException(HttpStatus.SERVICE_UNAVAILABLE,
					"Kafka no respondió a tiempo al " + accion + ". Revisa que los brokers estén activos.");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new KafkaAdminException(HttpStatus.SERVICE_UNAVAILABLE, "Se interrumpió la operación al " + accion + ".");
		}
	}

	static KafkaAdminException traducir(Throwable causa, String accion) {
		String detalle = causa == null ? "" : causa.getMessage();
		if (causa instanceof TopicExistsException) {
			return KafkaAdminException.conflicto("Ya existe un tópico con ese nombre.");
		}
		if (causa instanceof UnknownTopicOrPartitionException) {
			return new KafkaAdminException(HttpStatus.NOT_FOUND, "No existe el tópico indicado.");
		}
		if (causa instanceof GroupIdNotFoundException) {
			return new KafkaAdminException(HttpStatus.NOT_FOUND, "No existe el consumer group indicado.");
		}
		if (causa instanceof GroupNotEmptyException) {
			return KafkaAdminException.conflicto("El grupo tiene consumidores activos: detén el microservicio que lo usa.");
		}
		if (causa instanceof InvalidReplicationFactorException || causa instanceof InvalidPartitionsException
				|| causa instanceof InvalidConfigurationException || causa instanceof InvalidRequestException
				|| causa instanceof PolicyViolationException) {
			return KafkaAdminException.invalido("Kafka rechazó la operación: " + detalle);
		}
		if (causa instanceof org.apache.kafka.common.errors.TimeoutException) {
			return new KafkaAdminException(HttpStatus.SERVICE_UNAVAILABLE,
					"Kafka no respondió a tiempo al " + accion + ". Revisa que los brokers estén activos.");
		}
		log.error("Error inesperado de Kafka al {}", accion, causa);
		return new KafkaAdminException(HttpStatus.BAD_GATEWAY, "Kafka rechazó la operación al " + accion + ": " + detalle);
	}
}
