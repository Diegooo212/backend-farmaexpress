package cl.farmaexpress.analytics.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.farmaexpress.analytics.service.AnaliticaService;
import cl.farmaexpress.analytics.service.Tablero;

/** Panel de analítica en tiempo real (Operador y Administrador). El frontend lo consulta cada pocos segundos. */
@RestController
@RequestMapping("/api/analytics")
public class AnaliticaController {

	private final AnaliticaService analitica;

	public AnaliticaController(AnaliticaService analitica) {
		this.analitica = analitica;
	}

	@GetMapping("/tablero")
	public Tablero tablero() {
		return analitica.tablero();
	}
}
