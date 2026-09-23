package cl.farmaexpress.catalog.web;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.farmaexpress.catalog.service.MedicamentoService;
import cl.farmaexpress.catalog.web.dto.MedicamentoDtos.DescontarStockRequest;
import cl.farmaexpress.catalog.web.dto.MedicamentoDtos.MedicamentoRequest;
import cl.farmaexpress.catalog.web.dto.MedicamentoDtos.MedicamentoResponse;

@RestController
@RequestMapping("/api/catalog/medicamentos")
public class MedicamentoController {

	private final MedicamentoService service;

	public MedicamentoController(MedicamentoService service) {
		this.service = service;
	}

	@GetMapping
	public List<MedicamentoResponse> listar() {
		return service.listar();
	}

	@GetMapping("/{id}")
	public MedicamentoResponse obtener(@PathVariable Long id) {
		return service.obtener(id);
	}

	@PostMapping
	public ResponseEntity<MedicamentoResponse> crear(@Valid @RequestBody MedicamentoRequest request) {
		MedicamentoResponse creado = service.crear(request);
		return ResponseEntity.created(URI.create("/api/catalog/medicamentos/" + creado.id())).body(creado);
	}

	@PutMapping("/{id}")
	public MedicamentoResponse actualizar(@PathVariable Long id, @Valid @RequestBody MedicamentoRequest request) {
		return service.actualizar(id, request);
	}

	@PostMapping("/descontar-stock")
	public List<MedicamentoResponse> descontarStock(@Valid @RequestBody DescontarStockRequest request) {
		return service.descontarStock(request.items());
	}
}
