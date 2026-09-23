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

import cl.farmaexpress.catalog.domain.Medicamento;
import cl.farmaexpress.catalog.domain.MedicamentoRepository;
import cl.farmaexpress.catalog.web.dto.MedicamentoDtos.MedicamentoRequest;
import cl.farmaexpress.catalog.web.dto.MedicamentoDtos.MedicamentoResponse;
import cl.farmaexpress.catalog.web.dto.MedicamentoDtos.StockItem;

@Service
public class MedicamentoService {

	private final MedicamentoRepository repository;

	public MedicamentoService(MedicamentoRepository repository) {
		this.repository = repository;
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
		Medicamento medicamento = new Medicamento(request.sku(), request.nombre(), request.precio(), request.stock());
		return MedicamentoResponse.from(repository.save(medicamento));
	}

	@Transactional
	public MedicamentoResponse actualizar(Long id, MedicamentoRequest request) {
		Medicamento medicamento = buscar(id);
		if (repository.existsBySkuIgnoreCaseAndIdNot(request.sku().trim(), id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otro medicamento con el código " + request.sku().trim() + ".");
		}
		medicamento.actualizar(request.sku(), request.nombre(), request.precio(), request.stock());
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

	private Medicamento buscar(Long id) {
		return repository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el medicamento " + id + "."));
	}
}
