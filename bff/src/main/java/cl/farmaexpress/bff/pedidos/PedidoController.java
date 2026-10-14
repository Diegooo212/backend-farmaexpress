package cl.farmaexpress.bff.pedidos;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cl.farmaexpress.bff.auth.UsuarioActual;
import cl.farmaexpress.bff.auth.UsuarioService;
import cl.farmaexpress.bff.pedidos.PedidoService.CheckoutRequest;
import cl.farmaexpress.bff.pedidos.PedidoService.CheckoutResponse;
import cl.farmaexpress.bff.pedidos.PedidoService.PedidoResponse;
import cl.farmaexpress.bff.pedidos.PedidoService.ReintentoRequest;

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

	/** Checkout: reserva el stock del carrito y empieza el pago. El JWT se reenvía a inventory y payments. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request, @AuthenticationPrincipal Jwt jwt) {
		return pedidos.checkout(usuarioId(jwt), request, jwt.getTokenValue());
	}

	@GetMapping
	public List<PedidoResponse> listar(@AuthenticationPrincipal Jwt jwt) {
		return pedidos.listar(usuarioId(jwt));
	}

	@GetMapping("/despacho")
	public Map<String, Integer> reglasDespacho() {
		return pedidos.reglasDespacho();
	}

	@GetMapping("/{id}")
	public PedidoResponse obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return pedidos.obtener(usuarioId(jwt), id);
	}

	@PostMapping("/{id}/pago")
	public CheckoutResponse reintentarPago(@PathVariable Long id, @Valid @RequestBody ReintentoRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return pedidos.reintentarPago(usuarioId(jwt), id, request.metodoPago(), jwt.getTokenValue());
	}

	@DeleteMapping("/{id}")
	public PedidoResponse cancelar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return pedidos.cancelar(usuarioId(jwt), id, jwt.getTokenValue());
	}
}
