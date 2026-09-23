package cl.farmaexpress.bff.pedidos;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cl.farmaexpress.bff.auth.UsuarioActual;
import cl.farmaexpress.bff.auth.UsuarioService;
import cl.farmaexpress.bff.pedidos.PedidoService.PedidoResponse;

@RestController
@RequestMapping("/api/bff/orders")
public class PedidoController {

	private final PedidoService pedidos;
	private final UsuarioService usuarios;

	public PedidoController(PedidoService pedidos, UsuarioService usuarios) {
		this.pedidos = pedidos;
		this.usuarios = usuarios;
	}

	private Long usuarioId(Jwt jwt) {
		return usuarios.sincronizar(UsuarioActual.desde(jwt)).getId();
	}

	/** Compra lo que hay en el carrito guardado de la cuenta. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PedidoResponse comprar(@AuthenticationPrincipal Jwt jwt) {
		// El mismo access token de Entra ID se reenvía al catálogo, que también lo valida.
		return pedidos.comprar(usuarioId(jwt), jwt.getTokenValue());
	}

	@GetMapping
	public List<PedidoResponse> listar(@AuthenticationPrincipal Jwt jwt) {
		return pedidos.listar(usuarioId(jwt));
	}
}
