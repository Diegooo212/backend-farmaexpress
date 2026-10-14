package cl.farmaexpress.rabbitadmin.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cl.farmaexpress.rabbitadmin.service.RabbitAdminService;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.BindingRequest;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.BindingResponse;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.ColaCreada;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.ColaRequest;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.ColaResponse;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.ExchangeRequest;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.ExchangeResponse;

/**
 * API REST para administrar RabbitMQ (solo Administrador).
 *
 * <pre>
 * GET    /api/admin/rabbit/queues                         lista colas (mensajes, consumidores, DLQ)
 * POST   /api/admin/rabbit/queues                         crea una cola (opcional: DLQ, quórum, TTL, largo máximo)
 * DELETE /api/admin/rabbit/queues/{nombre}?forzar&amp;conDlq    elimina una cola
 * GET    /api/admin/rabbit/exchanges                      lista exchanges
 * POST   /api/admin/rabbit/exchanges                      crea un exchange (direct, topic, fanout, headers)
 * DELETE /api/admin/rabbit/exchanges/{nombre}             elimina un exchange
 * GET    /api/admin/rabbit/bindings                       lista bindings exchange → cola
 * POST   /api/admin/rabbit/bindings                       conecta una cola a un exchange
 * DELETE /api/admin/rabbit/bindings?exchange&amp;cola&amp;routingKey  desconecta
 * </pre>
 * Los errores responden Problem Details (400 validación, 404 no existe, 409 conflicto o recurso del
 * sistema, 502/503 RabbitMQ no disponible).
 */
@RestController
@RequestMapping("/api/admin/rabbit")
@Validated
public class RabbitAdminController {

	private final RabbitAdminService admin;

	public RabbitAdminController(RabbitAdminService admin) {
		this.admin = admin;
	}

	@GetMapping("/queues")
	public List<ColaResponse> colas() {
		return admin.listarColas();
	}

	@PostMapping("/queues")
	@ResponseStatus(HttpStatus.CREATED)
	public ColaCreada crearCola(@Valid @RequestBody ColaRequest request) {
		return admin.crearCola(request);
	}

	@DeleteMapping("/queues/{nombre}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminarCola(@PathVariable @Pattern(regexp = RabbitDtos.NOMBRE, message = "Nombre de cola inválido.") String nombre,
			@RequestParam(defaultValue = "false") boolean forzar, @RequestParam(defaultValue = "true") boolean conDlq) {
		admin.eliminarCola(nombre, forzar, conDlq);
	}

	@GetMapping("/exchanges")
	public List<ExchangeResponse> exchanges() {
		return admin.listarExchanges();
	}

	@PostMapping("/exchanges")
	@ResponseStatus(HttpStatus.CREATED)
	public ExchangeResponse crearExchange(@Valid @RequestBody ExchangeRequest request) {
		return admin.crearExchange(request);
	}

	@DeleteMapping("/exchanges/{nombre}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminarExchange(
			@PathVariable @Pattern(regexp = RabbitDtos.NOMBRE, message = "Nombre de exchange inválido.") String nombre) {
		admin.eliminarExchange(nombre);
	}

	@GetMapping("/bindings")
	public List<BindingResponse> bindings() {
		return admin.listarBindings();
	}

	@PostMapping("/bindings")
	@ResponseStatus(HttpStatus.CREATED)
	public BindingResponse crearBinding(@Valid @RequestBody BindingRequest request) {
		return admin.crearBinding(request);
	}

	@DeleteMapping("/bindings")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminarBinding(
			@RequestParam @NotBlank(message = "Indica el exchange.") @Pattern(regexp = RabbitDtos.NOMBRE, message = "Nombre de exchange inválido.") String exchange,
			@RequestParam @NotBlank(message = "Indica la cola.") @Pattern(regexp = RabbitDtos.NOMBRE, message = "Nombre de cola inválido.") String cola,
			@RequestParam(defaultValue = "") @Pattern(regexp = RabbitDtos.ROUTING_KEY, message = "Routing key inválida.") String routingKey) {
		admin.eliminarBinding(new BindingRequest(exchange, cola, routingKey));
	}
}
