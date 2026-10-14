package cl.farmaexpress.catalog.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import cl.farmaexpress.catalog.domain.Categoria;
import cl.farmaexpress.catalog.domain.CategoriaRepository;
import cl.farmaexpress.catalog.domain.Medicamento;
import cl.farmaexpress.catalog.domain.MedicamentoRepository;
import cl.farmaexpress.catalog.web.dto.MedicamentoDtos.CategoriaResponse;
import cl.farmaexpress.catalog.web.dto.MedicamentoDtos.MedicamentoRequest;
import cl.farmaexpress.catalog.web.dto.MedicamentoDtos.MedicamentoResponse;
import cl.farmaexpress.catalog.web.dto.MedicamentoDtos.StockItem;

@Service
public class MedicamentoService {

	private final MedicamentoRepository repository;
	private final CategoriaRepository categorias;

	public MedicamentoService(MedicamentoRepository repository, CategoriaRepository categorias) {
		this.repository = repository;
		this.categorias = categorias;
	}

	/** Categorías de la tienda con cuántos productos tiene cada una. */
	@Transactional(readOnly = true)
	public List<CategoriaResponse> categorias() {
		Map<Long, Long> porCategoria = repository.findAll().stream()
			.filter(m -> m.getCategoria() != null)
			.collect(Collectors.groupingBy(m -> m.getCategoria().getId(), Collectors.counting()));
		return categorias.findAllByOrderByOrdenAsc().stream()
			.map(c -> CategoriaResponse.from(c, porCategoria.getOrDefault(c.getId(), 0L)))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<MedicamentoResponse> listar() {
		return repository.findAllByOrderByIdAsc().stream().map(MedicamentoResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public MedicamentoResponse obtener(Long id) {
		return MedicamentoResponse.from(buscar(id));
	}

	@Transactional
	public MedicamentoResponse crear(MedicamentoRequest request) {
		if (repository.existsBySkuIgnoreCase(request.sku().trim())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un medicamento con el código " + request.sku().trim() + ".");
		}
		// Un producto nuevo parte sin stock: se carga ingresando lotes en el inventario.
		Medicamento medicamento = new Medicamento(request.sku(), request.nombre(), request.precio(),
				request.stock() != null ? request.stock() : 0);
		aplicarFicha(medicamento, request);
		return MedicamentoResponse.from(repository.save(medicamento));
	}

	@Transactional
	public MedicamentoResponse actualizar(Long id, MedicamentoRequest request) {
		Medicamento medicamento = buscar(id);
		if (repository.existsBySkuIgnoreCaseAndIdNot(request.sku().trim(), id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otro medicamento con el código " + request.sku().trim() + ".");
		}
		// El stock no se edita aquí: lo informa el inventario según sus lotes.
		medicamento.actualizar(request.sku(), request.nombre(), request.precio(), medicamento.getStock());
		aplicarFicha(medicamento, request);
		return MedicamentoResponse.from(medicamento);
	}

	/**
	 * Descuenta el stock de una compra completa en una sola transacción:
	 * si falta stock de cualquier producto, no se descuenta ninguno.
	 */
	@Transactional
	public List<MedicamentoResponse> descontarStock(List<StockItem> items) {
		// Si el mismo producto viene dos veces, se suman las cantidades.
		Map<Long, Integer> cantidades = items.stream()
			.collect(Collectors.toMap(StockItem::id, StockItem::cantidad, Integer::sum, LinkedHashMap::new));

		Map<Long, Medicamento> medicamentos = repository.findAllByIdForUpdate(cantidades.keySet()).stream()
			.collect(Collectors.toMap(Medicamento::getId, Function.identity()));

		cantidades.forEach((id, cantidad) -> {
			Medicamento medicamento = medicamentos.get(id);
			if (medicamento == null) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El medicamento " + id + " ya no está en el catálogo.");
			}
			if (!medicamento.tieneStock(cantidad)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay stock suficiente de " + medicamento.getNombre()
						+ ": quedan " + medicamento.getStock() + " y pediste " + cantidad + ".");
			}
		});

		cantidades.forEach((id, cantidad) -> medicamentos.get(id).descontarStock(cantidad));
		return cantidades.keySet().stream().map(medicamentos::get).map(MedicamentoResponse::from).toList();
	}

	/**
	 * Stock informado por el microservicio de inventario (evento inventario.stock-cambiado).
	 * Los productos que ya no están en el catálogo se ignoran.
	 */
	@Transactional
	public void sincronizarStock(Map<Long, int[]> stockPorProducto) {
		// Por producto: [máximo por pedido, total en todas las sucursales].
		repository.findAllById(stockPorProducto.keySet())
			.forEach(m -> m.fijarStock(stockPorProducto.get(m.getId())[0], stockPorProducto.get(m.getId())[1]));
	}

	/**
	 * Elimina el medicamento del catálogo. Los pedidos ya hechos no se ven afectados: guardan una
	 * copia del nombre y el precio. Si estaba en algún carrito, el BFF lo quita al leer el carrito.
	 */
	@Transactional
	public void eliminar(Long id) {
		repository.delete(buscar(id));
	}

	private void aplicarFicha(Medicamento medicamento, MedicamentoRequest request) {
		Categoria categoria = request.categoriaId() == null ? null
				: categorias.findById(request.categoriaId())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"No existe la categoría " + request.categoriaId() + "."));
		medicamento.actualizarFicha(categoria, request.descripcion(), request.laboratorio(), request.principioActivo(),
				request.presentacion(), Boolean.TRUE.equals(request.requiereReceta()),
				Boolean.TRUE.equals(request.bioequivalente()), request.imagenUrl());
	}

	private Medicamento buscar(Long id) {
		return repository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el medicamento " + id + "."));
	}
}
