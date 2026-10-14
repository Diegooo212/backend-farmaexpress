package cl.farmaexpress.bff.pedidos;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.farmaexpress.bff.auth.UsuarioActual;
import cl.farmaexpress.bff.pedidos.PedidoService.CambioEstadoRequest;
import cl.farmaexpress.bff.pedidos.PedidoService.PedidoGestion;
import cl.farmaexpress.bff.pedidos.PedidoService.PedidoResponse;

/** Panel de pedidos del personal de farmacia (Operador o Administrador, ver SecurityConfig). */
@RestController
@RequestMapping("/api/bff/gestion/pedidos")
public class GestionPedidosController {

	private final PedidoService pedidos;

	public GestionPedidosController(PedidoService pedidos) {
		this.pedidos = pedidos;
	}

	@GetMapping
	public List<PedidoGestion> porAtender(@RequestParam(defaultValue = "false") boolean entregados) {
		return pedidos.pedidosPorAtender(entregados);
	}

	@PutMapping("/{id}/estado")
	public PedidoResponse avanzar(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest cambio,
			@AuthenticationPrincipal Jwt jwt) {
		return pedidos.avanzar(id, cambio, UsuarioActual.desde(jwt).nombre());
	}
}
