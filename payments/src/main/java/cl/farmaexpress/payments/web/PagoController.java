package cl.farmaexpress.payments.web;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cl.farmaexpress.payments.domain.MetodoPago;
import cl.farmaexpress.payments.service.PagoService;
import cl.farmaexpress.payments.service.Usuario;
import cl.farmaexpress.payments.web.dto.PagoDtos.IniciarPagoRequest;
import cl.farmaexpress.payments.web.dto.PagoDtos.MetodoResponse;
import cl.farmaexpress.payments.web.dto.PagoDtos.PagoIniciadoResponse;
import cl.farmaexpress.payments.web.dto.PagoDtos.PagoResponse;
import cl.farmaexpress.payments.web.dto.PagoDtos.SimulacionRequest;

@RestController
@RequestMapping("/api/payments")
public class PagoController {

	private final PagoService pagos;

	public PagoController(PagoService pagos) {
		this.pagos = pagos;
	}

	/** Público: el checkout muestra qué medios están disponibles (según su circuit breaker). */
	@GetMapping("/metodos")
	public List<MetodoResponse> metodos() {
		return pagos.metodos();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PagoIniciadoResponse iniciar(@Valid @RequestBody IniciarPagoRequest request, @AuthenticationPrincipal Jwt jwt) {
		return pagos.iniciar(request, Usuario.desde(jwt));
	}

	@GetMapping("/{id}")
	public PagoResponse obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return pagos.obtener(id, Usuario.desde(jwt));
	}

	@GetMapping
	public List<PagoResponse> delPedido(@RequestParam Long pedidoId, @AuthenticationPrincipal Jwt jwt) {
		return pagos.delPedido(pedidoId, Usuario.desde(jwt));
	}

	/** Solo Administrador: simula la caída de un proveedor para demostrar el circuit breaker. */
	@PostMapping("/simulacion")
	public List<MetodoResponse> simular(@Valid @RequestBody SimulacionRequest request) {
		return pagos.simularFalla(request.metodo(), request.caido());
	}

	/**
	 * Público: aquí vuelve el navegador del cliente desde Webpay (formulario POST), Mercado Pago o
	 * Khipu (GET). Se confirma con el proveedor y se redirige al frontend (303 See Other).
	 */
	@RequestMapping(path = "/retorno/{metodo}", method = { RequestMethod.GET, RequestMethod.POST })
	public ResponseEntity<Void> retorno(@PathVariable String metodo, @RequestParam Map<String, String> parametros) {
		MetodoPago medio;
		try {
			medio = MetodoPago.desdeRuta(metodo);
		}
		catch (IllegalArgumentException ex) {
			return ResponseEntity.notFound().build();
		}
		String destino = pagos.procesarRetorno(medio, parametros);
		return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(destino)).build();
	}
}
