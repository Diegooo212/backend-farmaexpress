package cl.farmaexpress.bff.carrito;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.farmaexpress.bff.auth.UsuarioActual;
import cl.farmaexpress.bff.auth.UsuarioService;
import cl.farmaexpress.bff.carrito.CarritoDtos.CarritoRequest;
import cl.farmaexpress.bff.carrito.CarritoDtos.CarritoResponse;

@RestController
@RequestMapping("/api/bff/cart")
public class CarritoController {

	private final CarritoService carrito;
	private final UsuarioService usuarios;

	public CarritoController(CarritoService carrito, UsuarioService usuarios) {
		this.carrito = carrito;
		this.usuarios = usuarios;
	}

	private Long usuarioId(Jwt jwt) {
		return usuarios.sincronizar(UsuarioActual.desde(jwt)).getId();
	}

	@GetMapping
	public CarritoResponse obtener(@AuthenticationPrincipal Jwt jwt) {
		return carrito.obtener(usuarioId(jwt));
	}

	@PutMapping
	public CarritoResponse guardar(@Valid @RequestBody CarritoRequest request, @AuthenticationPrincipal Jwt jwt) {
		return carrito.guardar(usuarioId(jwt), request.items());
	}
}
