package cl.farmaexpress.bff.pedidos;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import cl.farmaexpress.bff.carrito.CarritoItem;
import cl.farmaexpress.bff.carrito.CarritoService;
import cl.farmaexpress.bff.catalog.CatalogClient;
import cl.farmaexpress.bff.catalog.CatalogClient.Medicamento;
import cl.farmaexpress.bff.catalog.CatalogClient.StockItem;

@Service
public class PedidoService {

	private final PedidoRepository pedidos;
	private final CarritoService carrito;
	private final CatalogClient catalog;

	public PedidoService(PedidoRepository pedidos, CarritoService carrito, CatalogClient catalog) {
		this.pedidos = pedidos;
		this.carrito = carrito;
		this.catalog = catalog;
	}

	public record ItemPedido(Long id, String nombre, Integer precio, Integer cantidad) {
	}

	public record PedidoResponse(Long id, Integer total, String creadoEn, List<ItemPedido> items) {

		static PedidoResponse from(Pedido p) {
			return new PedidoResponse(p.getId(), p.getTotal(), p.getCreadoEn().toString(), p.getItems().stream()
				.map(i -> new ItemPedido(i.getMedicamentoId(), i.getNombre(), i.getPrecioUnitario(), i.getCantidad()))
				.toList());
		}
	}

	/**
	 * Compra el carrito guardado de la cuenta: el catálogo descuenta el stock (todo o nada) y
	 * luego se registra el pedido con los precios de ese momento y se vacía el carrito.
	 */
	@Transactional
	public PedidoResponse comprar(Long usuarioId, String token) {
		List<CarritoItem> items = carrito.itemsDe(usuarioId);
		if (items.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tu carrito está vacío.");
		}

		List<StockItem> descontar = items.stream()
			.map(i -> new StockItem(i.getMedicamentoId(), i.getCantidad()))
			.toList();
		// Si falta stock, el catálogo responde 409 con el detalle y aquí no se guarda nada.
		Map<Long, Medicamento> vendidos = catalog.descontarStock(descontar, token).stream()
			.collect(Collectors.toMap(Medicamento::id, Function.identity()));

		Pedido pedido = new Pedido(usuarioId);
		items.forEach(i -> {
			Medicamento m = vendidos.get(i.getMedicamentoId());
			pedido.agregar(i.getMedicamentoId(), m.nombre(), m.precio(), i.getCantidad());
		});
		Pedido guardado = pedidos.save(pedido);
		carrito.vaciar(usuarioId);
		return PedidoResponse.from(guardado);
	}

	@Transactional(readOnly = true)
	public List<PedidoResponse> listar(Long usuarioId) {
		return pedidos.findAllByUsuarioIdOrderByCreadoEnDescIdDesc(usuarioId).stream()
			.map(PedidoResponse::from)
			.toList();
	}

}
