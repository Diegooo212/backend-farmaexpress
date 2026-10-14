package cl.farmaexpress.bff.pedidos;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import cl.farmaexpress.bff.auth.Usuario;
import cl.farmaexpress.bff.auth.UsuarioRepository;
import cl.farmaexpress.bff.carrito.CarritoDtos.CarritoResponse;
import cl.farmaexpress.bff.carrito.CarritoService;
import cl.farmaexpress.bff.catalog.CatalogClient;
import cl.farmaexpress.bff.catalog.CatalogClient.Medicamento;
import cl.farmaexpress.bff.eventos.PedidoEstadoCambiado;
import cl.farmaexpress.bff.inventory.InventoryClient;
import cl.farmaexpress.bff.inventory.InventoryClient.ItemReserva;
import cl.farmaexpress.bff.inventory.InventoryClient.Reserva;
import cl.farmaexpress.bff.payments.PaymentsClient;
import cl.farmaexpress.bff.payments.PaymentsClient.PagoIniciado;
import cl.farmaexpress.bff.prescriptions.PrescriptionsClient;
import cl.farmaexpress.bff.prescriptions.PrescriptionsClient.Receta;

/**
 * Checkout como en una tienda online:
 * <ol>
 * <li>se arma el pedido con los precios actuales del carrito;</li>
 * <li>inventory reserva el stock lote por lote (FEFO) en la farmacia de retiro o en el almacén central;</li>
 * <li>payments inicia el pago en el proveedor elegido y devuelve a dónde enviar al cliente.</li>
 * </ol>
 * El resultado llega después por RabbitMQ (pago.aprobado / pago.rechazado / reserva.expirada).
 */
@Service
public class PedidoService {

	private static final Logger log = LoggerFactory.getLogger(PedidoService.class);
	private static final String METODOS = "WEBPAY|MERCADOPAGO|KHIPU";
	/** Campo "detail" de un Problem Details, por si la respuesta no se puede convertir con Jackson. */
	private static final Pattern DETAIL = Pattern.compile("\"detail\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

	private final PedidoRepository pedidos;
	private final CarritoService carrito;
	private final InventoryClient inventory;
	private final PaymentsClient payments;
	private final CatalogClient catalog;
	private final PrescriptionsClient prescriptions;
	private final UsuarioRepository usuarios;
	private final ApplicationEventPublisher eventos;
	private final int costoDespacho;
	private final int despachoGratisDesde;

	public PedidoService(PedidoRepository pedidos, CarritoService carrito, InventoryClient inventory,
			PaymentsClient payments, CatalogClient catalog, PrescriptionsClient prescriptions, UsuarioRepository usuarios,
			ApplicationEventPublisher eventos, @Value("${farmaexpress.despacho.costo:2990}") int costoDespacho,
			@Value("${farmaexpress.despacho.gratis-desde:25000}") int despachoGratisDesde) {
		this.pedidos = pedidos;
		this.carrito = carrito;
		this.inventory = inventory;
		this.payments = payments;
		this.catalog = catalog;
		this.prescriptions = prescriptions;
		this.usuarios = usuarios;
		this.eventos = eventos;
		this.costoDespacho = costoDespacho;
		this.despachoGratisDesde = despachoGratisDesde;
	}

	public record CheckoutRequest(@NotNull(message = "Elige cómo quieres recibir tu pedido.") Pedido.Entrega entrega,
			Long sucursalId, @Size(max = 200, message = "La dirección es muy larga.") String direccion,
			@NotNull(message = "Elige un medio de pago.") @jakarta.validation.constraints.Pattern(regexp = METODOS, message = "Medio de pago no válido.") String metodoPago,
			Long recetaId) {
	}

	public record ReintentoRequest(
			@NotNull(message = "Elige un medio de pago.") @jakarta.validation.constraints.Pattern(regexp = METODOS, message = "Medio de pago no válido.") String metodoPago) {
	}

	public record ItemPedido(Long id, String nombre, Integer precio, Integer cantidad) {
	}

	public record PasoPedido(String estado, String fecha, String nota, String por) {
	}

	/** Cambio de estado que hace el personal (en preparación, listo para retiro, en camino, entregado). */
	public record CambioEstadoRequest(@NotNull(message = "Indica el nuevo estado.") Pedido.Estado estado,
			@Size(max = 300, message = "La nota es muy larga.") String nota) {
	}

	/** Pedido en el panel del personal: además, quién lo compró. */
	public record PedidoGestion(PedidoResponse pedido, String cliente, String email) {
	}

	public record PedidoResponse(Long id, String estado, Integer subtotal, Integer costoDespacho, Integer total,
			String entrega, Long sucursalId, String sucursal, String direccion, String metodoPago, Long pagoId,
			String detallePago, String expiraEn, String creadoEn, Long recetaId, List<ItemPedido> items,
			List<PasoPedido> historial) {

		static PedidoResponse from(Pedido p) {
			return new PedidoResponse(p.getId(), p.getEstado().name(), p.getTotal() - p.getCostoDespacho(),
					p.getCostoDespacho(), p.getTotal(), p.getEntrega() != null ? p.getEntrega().name() : null,
					p.getSucursalId(), p.getSucursalNombre(), p.getDireccion(), p.getMetodoPago(), p.getPagoId(),
					p.getDetallePago(), p.getExpiraEn() != null ? p.getExpiraEn().toString() : null,
					p.getCreadoEn().toString(), p.getRecetaId(), p.getItems().stream()
						.map(i -> new ItemPedido(i.getMedicamentoId(), i.getNombre(), i.getPrecioUnitario(), i.getCantidad()))
						.toList(),
					p.getHistorial().stream()
						.map(h -> new PasoPedido(h.getEstado().name(), h.getFecha().toString(), h.getNota(), h.getPor()))
						.toList());
		}
	}

	/** Si {@code redireccion} es null, el pago no se pudo iniciar y {@code error} dice por qué. */
	public record IntentoPago(Long pagoId, String modo, PaymentsClient.Redireccion redireccion, String error) {
	}

	public record CheckoutResponse(PedidoResponse pedido, IntentoPago pago) {
	}

	/** Costo de despacho: gratis desde cierto monto, como en la mayoría de las farmacias online. */
	public int costoDespacho(int subtotal) {
		return subtotal >= despachoGratisDesde ? 0 : costoDespacho;
	}

	public CheckoutResponse checkout(Long usuarioId, CheckoutRequest request, String token) {
		CarritoResponse actual = carrito.obtener(usuarioId);
		if (actual.items().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tu carrito está vacío.");
		}
		boolean retiro = request.entrega() == Pedido.Entrega.RETIRO;
		if (retiro && request.sucursalId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige la farmacia donde retirarás tu pedido.");
		}
		String direccion = request.direccion() == null ? "" : request.direccion().trim();
		if (!retiro && direccion.length() < 5) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe la dirección de despacho.");
		}

		Long recetaId = validarReceta(actual, request.recetaId(), token);

		Pedido pedido = new Pedido(usuarioId, request.entrega(), retiro ? null : direccion, request.metodoPago());
		pedido.asociarReceta(recetaId);
		actual.items().forEach(i -> pedido.agregar(i.id(), i.nombre(), i.precio(), i.cantidad()));
		if (!retiro) {
			pedido.cobrarDespacho(costoDespacho(actual.total()));
		}
		Pedido guardado = pedidos.save(pedido);

		Reserva reserva;
		try {
			List<ItemReserva> items = actual.items().stream()
				.map(i -> new ItemReserva(i.id(), i.cantidad(), i.nombre()))
				.toList();
			reserva = inventory.reservar(guardado.referencia(), retiro ? request.sucursalId() : null, items, token);
		}
		catch (RuntimeException ex) {
			// Sin stock (409) o inventario caído: el pedido no llega a existir.
			pedidos.delete(guardado);
			throw ex;
		}
		guardado.reservado(reserva.id(), reserva.sucursal().id(), reserva.sucursal().nombre(),
				Instant.parse(reserva.expiraEn()));
		guardado = pedidos.save(guardado);
		return new CheckoutResponse(PedidoResponse.from(guardado), iniciarPago(guardado, request.metodoPago(), token));
	}

	/** Pagar de nuevo (otro medio o el mismo) mientras la reserva siga vigente. */
	public CheckoutResponse reintentarPago(Long usuarioId, Long pedidoId, String metodo, String token) {
		Pedido pedido = delUsuario(usuarioId, pedidoId);
		if (pedido.getEstado() == Pedido.Estado.PAGADO) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido ya está pagado.");
		}
		if (!pedido.admitePago(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"La reserva de este pedido ya no está vigente. Vuelve a comprar desde el carrito.");
		}
		return new CheckoutResponse(PedidoResponse.from(pedido), iniciarPago(pedido, metodo, token));
	}

	public PedidoResponse cancelar(Long usuarioId, Long pedidoId, String token) {
		Pedido pedido = delUsuario(usuarioId, pedidoId);
		if (pedido.getEstado() != Pedido.Estado.PENDIENTE_PAGO && pedido.getEstado() != Pedido.Estado.PAGO_RECHAZADO) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido ya no se puede cancelar.");
		}
		if (pedido.getReservaId() != null) {
			try {
				inventory.liberar(pedido.getReservaId(), token);
			}
			catch (HttpClientErrorException.NotFound ex) {
				log.warn("La reserva {} del pedido {} ya no existe", pedido.getReservaId(), pedidoId);
			}
		}
		pedido.cancelar();
		return PedidoResponse.from(pedidos.save(pedido));
	}

	@Transactional(readOnly = true)
	public PedidoResponse obtener(Long usuarioId, Long pedidoId) {
		return PedidoResponse.from(delUsuario(usuarioId, pedidoId));
	}

	@Transactional(readOnly = true)
	public List<PedidoResponse> listar(Long usuarioId) {
		return pedidos.findAllByUsuarioIdOrderByCreadoEnDescIdDesc(usuarioId).stream().map(PedidoResponse::from).toList();
	}

	// ------------------------------------------------------------ panel del personal

	/** Estados que el personal tiene que atender (pagados y en curso). */
	private static final List<Pedido.Estado> POR_ATENDER = List.of(Pedido.Estado.PAGADO, Pedido.Estado.EN_PREPARACION,
			Pedido.Estado.LISTO_RETIRO, Pedido.Estado.EN_CAMINO);

	@Transactional(readOnly = true)
	public List<PedidoGestion> pedidosPorAtender(boolean incluirEntregados) {
		List<Pedido.Estado> estados = new java.util.ArrayList<>(POR_ATENDER);
		if (incluirEntregados) {
			estados.add(Pedido.Estado.ENTREGADO);
		}
		List<Pedido> lista = pedidos.findAllByEstadoInOrderByCreadoEnAscIdAsc(estados);
		Map<Long, Usuario> clientes = usuarios.findAllById(lista.stream().map(Pedido::getUsuarioId).distinct().toList())
			.stream()
			.collect(java.util.stream.Collectors.toMap(Usuario::getId, u -> u));
		return lista.stream()
			.map(p -> {
				Usuario u = clientes.get(p.getUsuarioId());
				return new PedidoGestion(PedidoResponse.from(p), u != null ? u.getNombre() : "Cliente",
						u != null ? u.getEmail() : null);
			})
			.toList();
	}

	/** El personal avanza el pedido y el cliente recibe el aviso (pedido.estado-cambiado). */
	@Transactional
	public PedidoResponse avanzar(Long pedidoId, CambioEstadoRequest cambio, String por) {
		Pedido pedido = pedidos.findById(pedidoId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el pedido " + pedidoId + "."));
		String nota = cambio.nota() == null || cambio.nota().isBlank() ? null : cambio.nota().trim();
		try {
			pedido.avanzar(cambio.estado(), nota, por);
		}
		catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
		}
		String cuenta = usuarios.findById(pedido.getUsuarioId()).map(Usuario::getEntraOid).orElse(null);
		if (cuenta != null) {
			eventos.publishEvent(new PedidoEstadoCambiado(UUID.randomUUID().toString(), pedido.getId(), cuenta,
					pedido.getEstado().name(), pedido.getEntrega() != null ? pedido.getEntrega().name() : null,
					pedido.getSucursalNombre(), nota, Instant.now()));
		}
		return PedidoResponse.from(pedido);
	}

	// ------------------------------------------------------------ eventos de RabbitMQ

	@Transactional
	public void pagoAprobado(Long pedidoId, Long pagoId, String metodo, Integer monto) {
		Pedido pedido = pedidos.findById(pedidoId).orElse(null);
		if (pedido == null || pedido.getEstado() == Pedido.Estado.PAGADO) {
			return;
		}
		String detalle = pedido.getEstado() == Pedido.Estado.PENDIENTE_PAGO || pedido.getEstado() == Pedido.Estado.PAGO_RECHAZADO
				? null : "Pago recibido con el pedido " + pedido.getEstado() + ": revisar el stock con inventario.";
		if (detalle != null) {
			log.error("Pedido {}: {}", pedidoId, detalle);
		}
		if (monto != null && !monto.equals(pedido.getTotal())) {
			log.error("Pedido {}: el monto pagado ({}) no coincide con el total ({})", pedidoId, monto, pedido.getTotal());
		}
		pedido.pagado(pagoId, metodo, detalle);
		carrito.vaciar(pedido.getUsuarioId());
	}

	@Transactional
	public void pagoRechazado(Long pedidoId, Long pagoId, String motivo) {
		pedidos.findById(pedidoId)
			.filter(p -> p.getEstado() == Pedido.Estado.PENDIENTE_PAGO)
			.filter(p -> pagoId == null || pagoId.equals(p.getPagoId()))
			.ifPresent(p -> p.pagoRechazado(motivo));
	}

	@Transactional
	public void reservaExpirada(String referencia) {
		if (referencia == null || !referencia.startsWith("PED-")) {
			return;
		}
		Long id;
		try {
			id = Long.valueOf(referencia.substring(4));
		}
		catch (NumberFormatException ex) {
			return;
		}
		pedidos.findById(id)
			.filter(p -> p.getEstado() == Pedido.Estado.PENDIENTE_PAGO || p.getEstado() == Pedido.Estado.PAGO_RECHAZADO)
			.ifPresent(Pedido::expirar);
	}

	// ------------------------------------------------------------ apoyo

	private IntentoPago iniciarPago(Pedido pedido, String metodo, String token) {
		try {
			PagoIniciado pago = payments.iniciar(pedido.getId(), pedido.getReservaId(), pedido.getTotal(), metodo,
					"Pedido FarmaExpress #" + pedido.getId(), token);
			pedido.pagoIniciado(pago.pagoId(), metodo);
			pedidos.save(pedido);
			return new IntentoPago(pago.pagoId(), pago.modo(), pago.redireccion(), null);
		}
		catch (RestClientResponseException ex) {
			// Proveedor caído o circuito abierto (503), u otro rechazo de la solicitud.
			String motivo = detalle(ex, "No pudimos iniciar el pago. Elige otro medio de pago.");
			pedido.pagoNoIniciado(metodo, motivo);
			pedidos.save(pedido);
			return new IntentoPago(null, null, null, motivo);
		}
		catch (ResourceAccessException ex) {
			String motivo = "El servicio de pagos no está disponible. Intenta de nuevo en unos minutos.";
			pedido.pagoNoIniciado(metodo, motivo);
			pedidos.save(pedido);
			return new IntentoPago(null, null, null, motivo);
		}
	}

	/** Estados en que una receta ya está "ocupada" por un pedido (no se puede usar en otro). */
	private static final List<Pedido.Estado> PEDIDOS_VIGENTES = List.of(Pedido.Estado.PENDIENTE_PAGO, Pedido.Estado.PAGADO,
			Pedido.Estado.PAGO_RECHAZADO, Pedido.Estado.EN_PREPARACION, Pedido.Estado.LISTO_RETIRO, Pedido.Estado.EN_CAMINO,
			Pedido.Estado.ENTREGADO);

	/** Estados de la receta en que el químico farmacéutico ya la aprobó y aún no se entrega. */
	private static final List<String> RECETA_VALIDADA = List.of("VALIDADA", "EN_PREPARACION", "LISTA_RETIRO");

	/**
	 * Venta bajo receta: si el carrito tiene productos que la exigen, el pedido necesita una receta de la
	 * cuenta, validada por el químico farmacéutico y que no respalde ya otra compra.
	 */
	private Long validarReceta(CarritoResponse actual, Long recetaId, String token) {
		Map<Long, Medicamento> catalogo = catalog.listar().stream()
			.collect(java.util.stream.Collectors.toMap(Medicamento::id, m -> m));
		List<String> conReceta = actual.items().stream()
			.filter(i -> catalogo.containsKey(i.id()) && catalogo.get(i.id()).conReceta())
			.map(i -> i.nombre())
			.toList();
		if (conReceta.isEmpty()) {
			return null;
		}
		String productos = String.join(", ", conReceta);
		if (recetaId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tu carrito tiene productos de venta bajo receta ("
					+ productos + "). Elige una receta validada por nuestro químico farmacéutico.");
		}
		Receta receta;
		try {
			receta = prescriptions.obtener(recetaId, token);
		}
		catch (HttpClientErrorException.NotFound ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No encontramos esa receta en tu cuenta.");
		}
		if (receta == null || !RECETA_VALIDADA.contains(receta.status())) {
			String estado = receta == null ? "" : receta.status();
			String motivo = switch (estado) {
				case "INGRESADA" -> "aún no ha sido validada por el químico farmacéutico. Te avisaremos apenas esté lista.";
				case "RECHAZADA" -> "fue rechazada. Revisa el motivo en Mis recetas y envía una nueva.";
				case "DISPENSADA" -> "ya fue entregada y no se puede volver a usar.";
				default -> "no está validada.";
			};
			throw new ResponseStatusException(HttpStatus.CONFLICT, "La receta #" + recetaId + " " + motivo);
		}
		if (pedidos.existsByRecetaIdAndEstadoIn(recetaId, PEDIDOS_VIGENTES)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"La receta #" + recetaId + " ya respalda otra compra. Cada receta sirve para un solo pedido.");
		}
		return recetaId;
	}

	private Pedido delUsuario(Long usuarioId, Long pedidoId) {
		return pedidos.findById(pedidoId)
			.filter(p -> p.esDe(usuarioId))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el pedido " + pedidoId + "."));
	}

	private static String detalle(RestClientResponseException ex, String porDefecto) {
		try {
			ProblemDetail problema = ex.getResponseBodyAs(ProblemDetail.class);
			if (problema != null && problema.getDetail() != null) {
				return problema.getDetail();
			}
		}
		catch (RuntimeException ignorada) {
			// Sin conversor de cuerpo (excepción creada a mano) o cuerpo que no es Problem Details.
		}
		Matcher m = DETAIL.matcher(ex.getResponseBodyAsString(StandardCharsets.UTF_8));
		return m.find() ? m.group(1).replace("\\\"", "\"") : porDefecto;
	}

	/** Solo para mostrar el costo de despacho en el checkout antes de confirmar. */
	public Map<String, Integer> reglasDespacho() {
		return Map.of("costo", costoDespacho, "gratisDesde", despachoGratisDesde);
	}
}
