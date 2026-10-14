package cl.farmaexpress.bff.catalog;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Público: categorías de la tienda. */
@RestController
@RequestMapping("/api/bff/catalog/categorias")
public class CategoriasController {

	private final CatalogClient catalog;

	public CategoriasController(CatalogClient catalog) {
		this.catalog = catalog;
	}

	@GetMapping
	public ResponseEntity<byte[]> listar() {
		return catalog.categorias();
	}
}
