package cl.farmaexpress.catalog.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.farmaexpress.catalog.service.MedicamentoService;
import cl.farmaexpress.catalog.web.dto.MedicamentoDtos.CategoriaResponse;

/** Público: categorías de la tienda para navegar el catálogo. */
@RestController
@RequestMapping("/api/catalog/categorias")
public class CategoriaController {

	private final MedicamentoService service;

	public CategoriaController(MedicamentoService service) {
		this.service = service;
	}

	@GetMapping
	public List<CategoriaResponse> listar() {
		return service.categorias();
	}
}
