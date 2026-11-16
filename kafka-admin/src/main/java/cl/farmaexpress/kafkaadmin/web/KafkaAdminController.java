package cl.farmaexpress.kafkaadmin.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cl.farmaexpress.kafkaadmin.service.KafkaAdminService;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.CambiarConfigRequest;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.ClusterResponse;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.CrearTopicoRequest;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.GrupoDetalle;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.GrupoResumen;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.ParticionesRequest;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.ReiniciarOffsetsRequest;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.TopicoDetalle;
import cl.farmaexpress.kafkaadmin.web.dto.KafkaDtos.TopicoResumen;

/**
 * API REST para administrar Kafka (solo Administrador):
 * <pre>
 * GET    /api/admin/kafka/cluster                                brokers y controlador
 * GET    /api/admin/kafka/topics?internos=false                  tópicos con particiones, réplicas y retención
 * GET    /api/admin/kafka/topics/{nombre}                        particiones (líder, réplicas, ISR, offsets) y configuración
 * POST   /api/admin/kafka/topics                                 crear un tópico con sus configuraciones
 * PUT    /api/admin/kafka/topics/{nombre}/config                 modificar configuraciones (vacío = valor por defecto)
 * POST   /api/admin/kafka/topics/{nombre}/partitions             aumentar particiones
 * DELETE /api/admin/kafka/topics/{nombre}                        eliminar un tópico
 * GET    /api/admin/kafka/consumer-groups                        grupos con estado, miembros y lag
 * GET    /api/admin/kafka/consumer-groups/{grupo}                miembros, particiones asignadas, offsets y lag
 * POST   /api/admin/kafka/consumer-groups/{grupo}/reset-offsets  mover offsets al inicio o al final
 * DELETE /api/admin/kafka/consumer-groups/{grupo}                eliminar un grupo sin consumidores
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/kafka")
@Validated
public class KafkaAdminController {

	private final KafkaAdminService kafka;

	public KafkaAdminController(KafkaAdminService kafka) {
		this.kafka = kafka;
	}

	@GetMapping("/cluster")
	public ClusterResponse cluster() {
		return kafka.cluster();
	}

	// ---------------------------------------------------------------- Tópicos

	@GetMapping("/topics")
	public List<TopicoResumen> topicos(@RequestParam(defaultValue = "false") boolean internos) {
		return kafka.listarTopicos(internos);
	}

	@GetMapping("/topics/{nombre}")
	public TopicoDetalle topico(@PathVariable @Pattern(regexp = KafkaDtos.NOMBRE, message = "Nombre de tópico inválido.") String nombre) {
		return kafka.topico(nombre);
	}

	@PostMapping("/topics")
	@ResponseStatus(HttpStatus.CREATED)
	public TopicoDetalle crearTopico(@Valid @RequestBody CrearTopicoRequest request) {
		return kafka.crearTopico(request);
	}

	@PutMapping("/topics/{nombre}/config")
	public TopicoDetalle cambiarConfig(
			@PathVariable @Pattern(regexp = KafkaDtos.NOMBRE, message = "Nombre de tópico inválido.") String nombre,
			@Valid @RequestBody CambiarConfigRequest request) {
		return kafka.cambiarConfig(nombre, request.config());
	}

	@PostMapping("/topics/{nombre}/partitions")
	public TopicoDetalle agregarParticiones(
			@PathVariable @Pattern(regexp = KafkaDtos.NOMBRE, message = "Nombre de tópico inválido.") String nombre,
			@Valid @RequestBody ParticionesRequest request) {
		return kafka.agregarParticiones(nombre, request.total());
	}

	@DeleteMapping("/topics/{nombre}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminarTopico(@PathVariable @Pattern(regexp = KafkaDtos.NOMBRE, message = "Nombre de tópico inválido.") String nombre) {
		kafka.eliminarTopico(nombre);
	}

	// ---------------------------------------------------------------- Consumer groups

	@GetMapping("/consumer-groups")
	public List<GrupoResumen> grupos() {
		return kafka.listarGrupos();
	}

	@GetMapping("/consumer-groups/{grupo}")
	public GrupoDetalle grupo(@PathVariable @Pattern(regexp = KafkaDtos.NOMBRE, message = "Nombre de grupo inválido.") String grupo) {
		return kafka.grupo(grupo);
	}

	@PostMapping("/consumer-groups/{grupo}/reset-offsets")
	public GrupoDetalle reiniciarOffsets(
			@PathVariable @Pattern(regexp = KafkaDtos.NOMBRE, message = "Nombre de grupo inválido.") String grupo,
			@Valid @RequestBody ReiniciarOffsetsRequest request) {
		return kafka.reiniciarOffsets(grupo, request.topico(), request.posicion());
	}

	@DeleteMapping("/consumer-groups/{grupo}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminarGrupo(@PathVariable @Pattern(regexp = KafkaDtos.NOMBRE, message = "Nombre de grupo inválido.") String grupo) {
		kafka.eliminarGrupo(grupo);
	}
}
