package cl.farmaexpress.inventory.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import cl.farmaexpress.inventory.domain.Lote;
import cl.farmaexpress.inventory.domain.LoteRepository;
import cl.farmaexpress.inventory.domain.Movimiento;
import cl.farmaexpress.inventory.domain.MovimientoRepository;
import cl.farmaexpress.inventory.domain.Reserva;
import cl.farmaexpress.inventory.domain.ReservaItem;
import cl.farmaexpress.inventory.domain.ReservaRepository;
import cl.farmaexpress.inventory.domain.StockPorSucursal;
import cl.farmaexpress.inventory.domain.Sucursal;
import cl.farmaexpress.inventory.domain.SucursalRepository;
import cl.farmaexpress.inventory.eventos.Eventos.ReservaExpirada;
import cl.farmaexpress.inventory.eventos.Eventos.StockCambiado;
import cl.farmaexpress.inventory.eventos.Eventos.StockProducto;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.CoberturaResponse;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.DisponibilidadResponse;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.Faltante;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.ItemSolicitado;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.LoteRequest;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.LoteResponse;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.MermaRequest;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.MovimientoResponse;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.ReservaItemResponse;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.ReservaRequest;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.ReservaResponse;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.ResumenResponse;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.SucursalResponse;

@Service
public class InventarioService {

	private static final Logger log = LoggerFactory.getLogger(InventarioService.class);

	private final SucursalRepository sucursales;
	private final LoteRepository lotes;
	private final MovimientoRepository movimientos;
	private final ReservaRepository reservas;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;
	private final Duration duracionReserva;
	private final int diasAlerta;

	public InventarioService(SucursalRepository sucursales, LoteRepository lotes, MovimientoRepository movimientos,
			ReservaRepository reservas, ApplicationEventPublisher eventos, Clock reloj,
			@Value("${farmaexpress.inventario.minutos-reserva}") long minutosReserva,
			@Value("${farmaexpress.inventario.dias-alerta-vencimiento}") int diasAlerta) {
		this.sucursales = sucursales;
		this.lotes = lotes;
		this.movimientos = movimientos;
		this.reservas = reservas;
		this.eventos = eventos;
		this.reloj = reloj;
		this.duracionReserva = Duration.ofMinutes(minutosReserva);
		this.diasAlerta = diasAlerta;
	}

	// ---------------------------------------------------------------- consultas públicas

	@Transactional(readOnly = true)
	public List<SucursalResponse> sucursales() {
		return sucursales.findAllByOrderByIdAsc().stream().map(SucursalResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<DisponibilidadResponse> disponibilidad(Long medicamentoId) {
		Map<Long, Integer> porSucursal = stockPorSucursal(Set.of(medicamentoId)).getOrDefault(medicamentoId, Map.of());
		return sucursales.findAllByOrderByIdAsc().stream()
			.map(s -> new DisponibilidadResponse(s.getId(), s.getNombre(), s.getTipo().name(),
					porSucursal.getOrDefault(s.getId(), 0)))
			.toList();
	}

	/** Para el checkout: qué sucursales tienen stock para todo el carrito y qué le falta a cada una. */
	@Transactional(readOnly = true)
	public List<CoberturaResponse> cobertura(List<ItemSolicitado> items) {
		Map<Long, ItemSolicitado> pedido = agrupar(items);
		Map<Long, Map<Long, Integer>> stock = stockPorSucursal(pedido.keySet());
		return sucursales.findAllByOrderByIdAsc().stream().map(s -> {
			List<Faltante> faltantes = pedido.values().stream()
				.map(i -> new Faltante(i.medicamentoId(), i.nombre(), i.cantidad(),
						stock.getOrDefault(i.medicamentoId(), Map.of()).getOrDefault(s.getId(), 0)))
				.filter(f -> f.disponible() < f.solicitado())
				.toList();
			return new CoberturaResponse(s.getId(), s.getNombre(), s.getTipo().name(), s.getDireccion(), s.getComuna(),
					faltantes.isEmpty(), faltantes);
		}).toList();
	}

	// ---------------------------------------------------------------- reservas (checkout)

	/**
	 * Reserva el stock de un pedido aplicando FEFO: para cada producto se toman primero las unidades
	 * del lote que vence antes (sin vencer), y se sigue con el siguiente lote hasta completar.
	 * Todo o nada: si a un producto le falta stock en la sucursal, no se reserva ninguno.
	 */
	@Transactional
	public ReservaResponse reservar(ReservaRequest request, Usuario usuario) {
		Sucursal sucursal = request.sucursalId() != null ? buscarSucursal(request.sucursalId())
				: sucursales.findFirstByTipoOrderByIdAsc(Sucursal.Tipo.CENTRAL)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No hay un almacén central configurado."));
		Instant ahora = reloj.instant();
		LocalDate hoy = LocalDate.now(reloj);
		Reserva reserva = new Reserva(request.referencia(), usuario.id(), sucursal, ahora, ahora.plus(duracionReserva));

		// 1) Se revisa todo el pedido antes de tocar un solo lote (todo o nada).
		Map<ItemSolicitado, List<Lote>> plan = new LinkedHashMap<>();
		for (ItemSolicitado item : agrupar(request.items()).values()) {
			List<Lote> fefo = lotes.findVendiblesFefo(item.medicamentoId(), sucursal.getId(), hoy);
			int disponible = fefo.stream().mapToInt(Lote::getDisponible).sum();
			if (disponible < item.cantidad()) {
				String producto = item.nombre() != null ? item.nombre() : "el producto " + item.medicamentoId();
				throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay stock suficiente de " + producto + " en "
						+ sucursal.getNombre() + ": quedan " + disponible + " y pediste " + item.cantidad() + ".");
			}
			plan.put(item, fefo);
		}

		// 2) FEFO: cada producto se toma del lote que vence primero, y así sucesivamente.
		for (Map.Entry<ItemSolicitado, List<Lote>> paso : plan.entrySet()) {
			ItemSolicitado item = paso.getKey();
			int restante = item.cantidad();
			for (Lote lote : paso.getValue()) {
				if (restante == 0) {
					break;
				}
				int tomar = Math.min(restante, lote.getDisponible());
				lote.reservar(tomar);
				reserva.agregar(lote, tomar);
				movimientos.save(new Movimiento(lote, Movimiento.Tipo.RESERVA, tomar, "Pedido " + request.referencia(),
						usuario.nombre(), ahora));
				restante -= tomar;
			}
		}
		Reserva guardada = reservas.save(reserva);
		publicarStock(idsDe(guardada));
		return aRespuesta(guardada);
	}

	@Transactional(readOnly = true)
	public ReservaResponse obtenerReserva(Long id, Usuario usuario) {
		return aRespuesta(buscarReservaVisible(id, usuario));
	}

	/** El cliente canceló el pedido antes de pagar: las unidades vuelven a estar disponibles. */
	@Transactional
	public ReservaResponse liberar(Long id, Usuario usuario) {
		Reserva reserva = buscarReservaVisible(id, usuario);
		if (reserva.estaActiva()) {
			devolver(reserva, Reserva.Estado.LIBERADA, "Pedido cancelado", usuario.nombre());
		}
		return aRespuesta(reserva);
	}

	/**
	 * El pago se aprobó (evento pago.aprobado): las unidades reservadas salen definitivamente.
	 * Es idempotente: si la reserva ya estaba confirmada, no hace nada.
	 */
	@Transactional
	public void confirmar(Long reservaId, String referenciaPago) {
		Reserva reserva = reservas.findWithItemsById(reservaId).orElse(null);
		if (reserva == null) {
			log.warn("Pago aprobado para la reserva {}, que no existe", reservaId);
			return;
		}
		if (reserva.getEstado() == Reserva.Estado.CONFIRMADA) {
			return;
		}
		if (!reserva.estaActiva()) {
			// El pago llegó después de que la reserva expiró: se deja registro para revisarlo a mano.
			log.error("Pago {} aprobado para la reserva {} en estado {}: revisar stock del pedido {}", referenciaPago,
					reservaId, reserva.getEstado(), reserva.getReferencia());
			return;
		}
		Instant ahora = reloj.instant();
		for (ReservaItem item : reserva.getItems()) {
			item.getLote().confirmarSalida(item.getCantidad());
			movimientos.save(new Movimiento(item.getLote(), Movimiento.Tipo.SALIDA, item.getCantidad(),
					"Pedido " + reserva.getReferencia() + " pagado (" + referenciaPago + ")", "sistema", ahora));
		}
		reserva.cambiarEstado(Reserva.Estado.CONFIRMADA);
	}

	/** Reservas sin pagar a tiempo: se liberan y se avisa al BFF para que marque el pedido como expirado. */
	@Transactional
	public int expirarVencidas() {
		List<Reserva> vencidas = reservas.findAllByEstadoAndExpiraEnBefore(Reserva.Estado.ACTIVA, reloj.instant());
		for (Reserva r : vencidas) {
			Reserva reserva = reservas.findWithItemsById(r.getId()).orElseThrow();
			devolver(reserva, Reserva.Estado.EXPIRADA, "Reserva expirada sin pago", "sistema");
			eventos.publishEvent(new ReservaExpirada(UUID.randomUUID().toString(), reserva.getId(),
					reserva.getReferencia(), reloj.instant()));
		}
		return vencidas.size();
	}

	// ---------------------------------------------------------------- gestión de lotes (personal)

	@Transactional(readOnly = true)
	public List<LoteResponse> lotes(Long sucursalId, Long medicamentoId, String estado) {
		LocalDate hoy = LocalDate.now(reloj);
		return lotes.findAllFefo().stream()
			.filter(l -> sucursalId == null || l.getSucursal().getId().equals(sucursalId))
			.filter(l -> medicamentoId == null || l.getMedicamentoId().equals(medicamentoId))
			.map(l -> aRespuesta(l, hoy))
			.filter(l -> estado == null || estado.isBlank() || l.estado().equalsIgnoreCase(estado))
			.toList();
	}

	@Transactional
	public LoteResponse ingresar(LoteRequest request, Usuario usuario) {
		LocalDate hoy = LocalDate.now(reloj);
		if (!request.fechaVencimiento().isAfter(hoy)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede ingresar un lote vencido.");
		}
		Sucursal sucursal = buscarSucursal(request.sucursalId());
		String numero = request.numeroLote().trim().toUpperCase();
		if (lotes.existsByMedicamentoIdAndSucursal_IdAndNumeroLoteIgnoreCase(request.medicamentoId(), sucursal.getId(), numero)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"El lote " + numero + " ya está registrado para ese producto en " + sucursal.getNombre() + ".");
		}
		Instant ahora = reloj.instant();
		Lote lote = lotes.save(new Lote(request.medicamentoId(), sucursal, numero, request.fechaVencimiento(),
				request.cantidad(), ahora));
		movimientos.save(new Movimiento(lote, Movimiento.Tipo.INGRESO, request.cantidad(), "Ingreso de mercadería",
				usuario.nombre(), ahora));
		publicarStock(Set.of(request.medicamentoId()));
		return aRespuesta(lote, hoy);
	}

	/** Baja de unidades (vencidas, dañadas o perdidas). */
	@Transactional
	public LoteResponse darDeBaja(Long loteId, MermaRequest request, Usuario usuario) {
		Lote lote = lotes.findById(loteId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el lote " + loteId + "."));
		if (request.cantidad() > lote.getDisponible()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"El lote " + lote.getNumeroLote() + " tiene " + lote.getDisponible() + " unidades disponibles.");
		}
		lote.darDeBaja(request.cantidad());
		movimientos.save(new Movimiento(lote, Movimiento.Tipo.MERMA, request.cantidad(), request.motivo().trim(),
				usuario.nombre(), reloj.instant()));
		publicarStock(Set.of(lote.getMedicamentoId()));
		return aRespuesta(lote, LocalDate.now(reloj));
	}

	@Transactional(readOnly = true)
	public List<MovimientoResponse> movimientos(Long loteId) {
		if (!lotes.existsById(loteId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el lote " + loteId + ".");
		}
		return movimientos.findAllByLoteIdOrderByFechaDescIdDesc(loteId).stream()
			.map(m -> new MovimientoResponse(m.getId(), m.getTipo().name(), m.getCantidad(), m.getReferencia(),
					m.getUsuario(), m.getFecha().toString()))
			.toList();
	}

	@Transactional(readOnly = true)
	public ResumenResponse resumen() {
		List<LoteResponse> todos = lotes(null, null, null);
		int vigentes = 0, vendibles = 0, porVencer = 0, unidadesPorVencer = 0, vencidos = 0, unidadesVencidas = 0;
		for (LoteResponse l : todos) {
			switch (l.estado()) {
				case "VIGENTE" -> {
					vigentes++;
					vendibles += l.disponible();
				}
				case "POR_VENCER" -> {
					porVencer++;
					unidadesPorVencer += l.disponible();
					vendibles += l.disponible();
				}
				case "VENCIDO" -> {
					vencidos++;
					unidadesVencidas += l.disponible();
				}
				default -> {
				}
			}
		}
		return new ResumenResponse(vigentes, vendibles, porVencer, unidadesPorVencer, vencidos, unidadesVencidas,
				reservas.countByEstado(Reserva.Estado.ACTIVA), diasAlerta);
	}

	/** Stock vendible de todos los productos, para sincronizar el catálogo al iniciar. */
	@Transactional(readOnly = true)
	public void publicarTodoElStock() {
		List<Long> ids = lotes.medicamentosConLotes();
		if (!ids.isEmpty()) {
			publicarStock(ids);
		}
	}

	// ---------------------------------------------------------------- apoyo

	private void devolver(Reserva reserva, Reserva.Estado estado, String motivo, String usuario) {
		Instant ahora = reloj.instant();
		for (ReservaItem item : reserva.getItems()) {
			item.getLote().liberar(item.getCantidad());
			movimientos.save(new Movimiento(item.getLote(), Movimiento.Tipo.LIBERACION, item.getCantidad(),
					motivo + " (pedido " + reserva.getReferencia() + ")", usuario, ahora));
		}
		reserva.cambiarEstado(estado);
		publicarStock(idsDe(reserva));
	}

	/**
	 * Publica cuánto se puede comprar de cada producto en un solo pedido (se envía después del commit).
	 * Un pedido se entrega desde un único lugar (la farmacia de retiro o el almacén central), así que no
	 * sirve sumar sucursales: es el stock vendible de la sucursal que más tiene. Así el carrito nunca
	 * permite más de lo que alguna sucursal puede entregar.
	 */
	private void publicarStock(Collection<Long> medicamentos) {
		lotes.flush();
		Map<Long, Map<Long, Integer>> stock = stockPorSucursal(medicamentos);
		List<StockProducto> items = medicamentos.stream()
			.distinct()
			.map(id -> {
				Map<Long, Integer> porSucursal = stock.getOrDefault(id, Map.of());
				return new StockProducto(id, porSucursal.values().stream().mapToInt(Integer::intValue).max().orElse(0),
						porSucursal.values().stream().mapToInt(Integer::intValue).sum());
			})
			.toList();
		eventos.publishEvent(new StockCambiado(UUID.randomUUID().toString(), items, reloj.instant()));
	}

	private Map<Long, Map<Long, Integer>> stockPorSucursal(Collection<Long> medicamentos) {
		Map<Long, Map<Long, Integer>> resultado = new HashMap<>();
		for (StockPorSucursal fila : lotes.stockVendible(medicamentos, LocalDate.now(reloj))) {
			resultado.computeIfAbsent(fila.medicamentoId(), k -> new HashMap<>())
				.put(fila.sucursalId(), fila.disponible().intValue());
		}
		return resultado;
	}

	private static Map<Long, ItemSolicitado> agrupar(List<ItemSolicitado> items) {
		// Si el mismo producto viene dos veces, se suman las cantidades.
		return items.stream().collect(Collectors.toMap(ItemSolicitado::medicamentoId, i -> i,
				(a, b) -> new ItemSolicitado(a.medicamentoId(), a.cantidad() + b.cantidad(), a.nombre()),
				LinkedHashMap::new));
	}

	private static Set<Long> idsDe(Reserva reserva) {
		return reserva.getItems().stream().map(ReservaItem::getMedicamentoId).collect(Collectors.toSet());
	}

	private Sucursal buscarSucursal(Long id) {
		return sucursales.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la sucursal " + id + "."));
	}

	/** Si la reserva es de otra cuenta se responde 404 (el personal de farmacia ve todas). */
	private Reserva buscarReservaVisible(Long id, Usuario usuario) {
		Reserva reserva = reservas.findWithItemsById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la reserva " + id + "."));
		if (!usuario.esPersonalFarmacia() && !reserva.perteneceA(usuario.id())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la reserva " + id + ".");
		}
		return reserva;
	}

	private LoteResponse aRespuesta(Lote l, LocalDate hoy) {
		long dias = ChronoUnit.DAYS.between(hoy, l.getFechaVencimiento());
		String estado;
		if (l.getDisponible() == 0 && l.getReservada() == 0) {
			estado = "AGOTADO";
		}
		else if (l.estaVencido(hoy)) {
			estado = "VENCIDO";
		}
		else if (dias <= diasAlerta) {
			estado = "POR_VENCER";
		}
		else {
			estado = "VIGENTE";
		}
		return new LoteResponse(l.getId(), l.getMedicamentoId(), l.getSucursal().getId(), l.getSucursal().getNombre(),
				l.getNumeroLote(), l.getFechaVencimiento(), dias, l.getCantidadInicial(), l.getDisponible(),
				l.getReservada(), estado);
	}

	private static ReservaResponse aRespuesta(Reserva r) {
		List<ReservaItemResponse> items = new ArrayList<>();
		for (ReservaItem i : r.getItems()) {
			items.add(new ReservaItemResponse(i.getMedicamentoId(), i.getLote().getId(), i.getLote().getNumeroLote(),
					i.getLote().getFechaVencimiento(), i.getCantidad()));
		}
		return new ReservaResponse(r.getId(), r.getReferencia(), r.getEstado().name(),
				SucursalResponse.from(r.getSucursal()), r.getExpiraEn().toString(), items);
	}
}
