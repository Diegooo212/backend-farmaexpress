package cl.farmaexpress.analytics.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.farmaexpress.analytics.domain.StockProducto;
import cl.farmaexpress.analytics.domain.StockProductoRepository;
import cl.farmaexpress.analytics.domain.Venta;
import cl.farmaexpress.analytics.domain.VentaRepository;
import cl.farmaexpress.analytics.eventos.StockActualizado;
import cl.farmaexpress.analytics.eventos.VentaRegistrada;
import cl.farmaexpress.analytics.service.Tablero.ProductoVendido;
import cl.farmaexpress.analytics.service.Tablero.PuntoHora;
import cl.farmaexpress.analytics.service.Tablero.Resumen;
import cl.farmaexpress.analytics.service.Tablero.Segmento;
import cl.farmaexpress.analytics.service.Tablero.StockBajo;
import cl.farmaexpress.analytics.service.Tablero.VentaReciente;

/**
 * Lógica de la analítica: registra ventas y stock a partir de los eventos (ya validados) y arma el panel.
 * No conoce Kafka: recibe los datos del evento y, para trazabilidad, su partición y offset.
 */
@Service
public class AnaliticaService {

	private static final Logger log = LoggerFactory.getLogger(AnaliticaService.class);
	private static final int HORAS_GRAFICO = 12;

	private final VentaRepository ventas;
	private final StockProductoRepository stock;
	private final Clock reloj;
	private final ZoneId zona;
	private final int umbralStockBajo;

	public AnaliticaService(VentaRepository ventas, StockProductoRepository stock, Clock reloj,
			@Value("${farmaexpress.analytics.zona}") ZoneId zona,
			@Value("${farmaexpress.analytics.umbral-stock-bajo}") int umbralStockBajo) {
		this.ventas = ventas;
		this.stock = stock;
		this.reloj = reloj;
		this.zona = zona;
		this.umbralStockBajo = umbralStockBajo;
	}

	/**
	 * Registra la venta una sola vez: Kafka garantiza entrega "al menos una vez", así que un evento puede
	 * llegar repetido (por ejemplo, tras un rebalanceo). El pedido es la clave primaria.
	 * @return false si el pedido ya estaba registrado
	 */
	@Transactional
	public boolean registrarVenta(VentaRegistrada evento, int particion, long offset) {
		if (ventas.existsById(evento.pedidoId())) {
			log.info("Venta del pedido {} ya registrada: se ignora el evento repetido {}", evento.pedidoId(), evento.eventoId());
			return false;
		}
		Venta venta = new Venta(evento.pedidoId(), evento.eventoId(), evento.fecha(), reloj.instant(), evento.entrega(),
				evento.sucursalId(), evento.sucursalNombre(), evento.metodoPago(), evento.costoDespacho(), evento.total(),
				particion, offset);
		evento.items().forEach(i -> venta.agregar(i.medicamentoId(), i.nombre(), i.cantidad(), i.precioUnitario()));
		ventas.save(venta);
		log.info("Venta registrada: pedido {} por ${} ({} unidades, {})", evento.pedidoId(), evento.total(),
				evento.unidades(), evento.metodoPago());
		return true;
	}

	/** Guarda el último stock conocido del producto; ignora un evento más antiguo que el vigente. */
	@Transactional
	public void actualizarStock(StockActualizado evento, int particion, long offset) {
		StockProducto actual = stock.findById(evento.medicamentoId()).orElseGet(() -> new StockProducto(evento.medicamentoId()));
		if (actual.esMasNuevoQue(evento.fecha())) {
			log.info("Stock del producto {}: se ignora un evento atrasado ({})", evento.medicamentoId(), evento.fecha());
			return;
		}
		actual.actualizar(evento.stockPorPedido(), evento.stockTotal(), evento.fecha(), particion, offset);
		stock.save(actual);
		if (evento.stockTotal() <= umbralStockBajo) {
			log.warn("Stock bajo: producto {} con {} unidades en total", evento.medicamentoId(), evento.stockTotal());
		}
	}

	/** Lápida (mensaje sin contenido) en el tópico compactado: el producto ya no tiene stock publicado. */
	@Transactional
	public void eliminarStock(Long medicamentoId) {
		stock.deleteById(medicamentoId);
		log.info("Stock del producto {} eliminado (lápida del tópico compactado)", medicamentoId);
	}

	@Transactional(readOnly = true)
	public Tablero tablero() {
		Instant ahora = reloj.instant();
		Instant inicioDia = ahora.atZone(zona).toLocalDate().atStartOfDay(zona).toInstant();

		List<PuntoHora> porHora = ventasPorHora(ahora);
		List<ProductoVendido> masVendidos = ventas.masVendidos(PageRequest.of(0, 5)).stream()
			.map(f -> new ProductoVendido((Long) f[0], (String) f[1], numero(f[2]), numero(f[3])))
			.toList();
		List<Segmento> porSucursal = ventas.porSucursal().stream()
			.map(f -> new Segmento(f[0] == null ? "Almacén central (despacho)" : (String) f[0], numero(f[1]), numero(f[2])))
			.toList();
		List<Segmento> porMetodo = ventas.porMetodoPago().stream()
			.map(f -> new Segmento((String) f[0], numero(f[1]), numero(f[2])))
			.toList();
		List<VentaReciente> recientes = ventas.findAllByOrderByRecibidaEnDesc(PageRequest.of(0, 8)).stream()
			.map(v -> new VentaReciente(v.getPedidoId(), v.getFecha(), v.getRecibidaEn(),
					Math.max(0, Duration.between(v.getFecha(), v.getRecibidaEn()).toMillis()), v.getTotal(), v.getUnidades(),
					v.getEntrega(), v.getSucursalNombre(), v.getMetodoPago(), v.getParticion(), v.getOffsetKafka()))
			.toList();
		List<StockBajo> stockBajo = stock.findByStockTotalLessThanEqualOrderByStockTotalAscMedicamentoIdAsc(umbralStockBajo)
			.stream()
			.map(s -> new StockBajo(s.getMedicamentoId(), s.getStockTotal(), s.getStockPorPedido(), s.getActualizadoEn()))
			.toList();

		return new Tablero(ahora, resumen(inicioDia), resumen(Instant.EPOCH), porHora, masVendidos, porSucursal, porMetodo,
				recientes, stockBajo, umbralStockBajo);
	}

	private Resumen resumen(Instant desde) {
		Object[] f = ventas.totalesDesde(desde).get(0);
		long cantidad = numero(f[0]);
		long ingresos = numero(f[1]);
		return new Resumen(cantidad, ingresos, numero(f[2]), cantidad == 0 ? 0 : Math.round((double) ingresos / cantidad));
	}

	/** Ventas e ingresos de cada una de las últimas 12 horas (incluida la actual). */
	private List<PuntoHora> ventasPorHora(Instant ahora) {
		Instant horaActual = ahora.truncatedTo(ChronoUnit.HOURS);
		Instant desde = horaActual.minus(HORAS_GRAFICO - 1L, ChronoUnit.HOURS);
		Map<Instant, long[]> horas = new LinkedHashMap<>();
		for (int i = 0; i < HORAS_GRAFICO; i++) {
			horas.put(desde.plus(i, ChronoUnit.HOURS), new long[2]);
		}
		for (Venta v : ventas.findByFechaGreaterThanEqual(desde)) {
			long[] acumulado = horas.get(v.getFecha().truncatedTo(ChronoUnit.HOURS));
			if (acumulado != null) {
				acumulado[0]++;
				acumulado[1] += v.getTotal();
			}
		}
		List<PuntoHora> resultado = new ArrayList<>();
		horas.forEach((hora, a) -> resultado.add(new PuntoHora(hora, a[0], a[1])));
		return resultado;
	}

	private static long numero(Object valor) {
		return valor == null ? 0 : ((Number) valor).longValue();
	}
}
