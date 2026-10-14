package cl.farmaexpress.bff.payments;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bff/payments")
public class PaymentsController {

	private final PaymentsClient payments;

	public PaymentsController(PaymentsClient payments) {
		this.payments = payments;
	}

	/** Público: medios de pago y si están disponibles (circuit breaker de cada proveedor). */
	@GetMapping("/metodos")
	public ResponseEntity<byte[]> metodos() {
		return payments.reenviar(HttpMethod.GET, "/metodos", null, null);
	}

	@GetMapping("/{id}")
	public ResponseEntity<byte[]> obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return payments.reenviar(HttpMethod.GET, "/" + id, null, jwt.getTokenValue());
	}

	/** Solo Administrador: simular la caída de un proveedor (demostración del circuit breaker). */
	@PostMapping("/simulacion")
	public ResponseEntity<byte[]> simular(@RequestBody String body, @AuthenticationPrincipal Jwt jwt) {
		return payments.reenviar(HttpMethod.POST, "/simulacion", body, jwt.getTokenValue());
	}

	/**
	 * Público: el navegador vuelve aquí desde Webpay (formulario POST), Mercado Pago o Khipu (GET).
	 * Pagos confirma con el proveedor y responde 303 hacia la página de resultado del frontend.
	 */
	@RequestMapping(path = "/retorno/{metodo}", method = { RequestMethod.GET, RequestMethod.POST })
	public ResponseEntity<byte[]> retorno(@PathVariable String metodo, @RequestParam MultiValueMap<String, String> parametros) {
		return payments.retorno(metodo, parametros);
	}
}
