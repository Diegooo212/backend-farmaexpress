package cl.farmaexpress.bff.carrito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.farmaexpress.bff.carrito.CarritoDtos.CarritoResponse;
import cl.farmaexpress.bff.carrito.CarritoDtos.ItemRequest;
import cl.farmaexpress.bff.carrito.CarritoDtos.ItemResponse;
import cl.farmaexpress.bff.catalog.CatalogClient;
import cl.farmaexpress.bff.catalog.CatalogClient.Medicamento;

/**
 * Carrito guardado por cuenta. Solo se guardan id y cantidad; nombre, precio y stock se leen
 * siempre del catálogo, así el carrito nunca muestra un precio desactualizado.
 */
@Service
public class CarritoService {

	private final CarritoItemRepository items;
	private final CatalogClient catalog;

	public CarritoService(CarritoItemRepository items, CatalogClient catalog) {
		this.items = items;
		this.catalog = catalog;
	}

	@Transactional(readOnly = true)
	public CarritoResponse obtener(Long usuarioId) {
		return armar(items.findAllByUsuarioIdOrderByIdAsc(usuarioId), catalogoPorId());
	}

	/**
	 * Reemplaza el carrito completo. Las cantidades se ajustan al stock disponible y los
	 * productos que ya no existen o están agotados se quitan.
	 */
	@Transactional
	public CarritoResponse guardar(Long usuarioId, List<ItemRequest> nuevos) {
		Map<Long, Medicamento> catalogo = catalogoPorId();

		Map<Long, Integer> cantidades = nuevos.stream()
			.collect(Collectors.toMap(ItemRequest::id, ItemRequest::cantidad, Integer::sum, LinkedHashMap::new));

		items.vaciar(usuarioId);
		List<CarritoItem> guardados = new ArrayList<>();
		cantidades.forEach((id, cantidad) -> {
			Medicamento medicamento = catalogo.get(id);
			if (medicamento == null || medicamento.stock() <= 0) {
				return;
			}
			guardados.add(new CarritoItem(usuarioId, id, Math.min(cantidad, medicamento.stock())));
		});
		return armar(items.saveAll(guardados), catalogo);
	}

	@Transactional
	public void vaciar(Long usuarioId) {
		items.vaciar(usuarioId);
	}

	@Transactional(readOnly = true)
	public List<CarritoItem> itemsDe(Long usuarioId) {
		return items.findAllByUsuarioIdOrderByIdAsc(usuarioId);
	}

	private CarritoResponse armar(List<CarritoItem> guardados, Map<Long, Medicamento> catalogo) {
		List<ItemResponse> respuesta = guardados.stream()
			.filter(item -> catalogo.containsKey(item.getMedicamentoId()))
			.map(item -> {
				Medicamento m = catalogo.get(item.getMedicamentoId());
				return new ItemResponse(m.id(), m.nombre(), m.precio(), item.getCantidad(), m.stock());
			})
			.toList();
		return CarritoResponse.de(respuesta);
	}

	private Map<Long, Medicamento> catalogoPorId() {
		return catalog.listar().stream().collect(Collectors.toMap(Medicamento::id, Function.identity()));
	}

}
