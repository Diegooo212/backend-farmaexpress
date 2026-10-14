package cl.farmaexpress.payments.proveedores;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import cl.farmaexpress.payments.domain.MetodoPago;
import cl.farmaexpress.payments.domain.Pago;

/**
 * Khipu (API v3, transferencia simplificada).
 * <ol>
 * <li>{@code POST /v3/payments} crea el cobro y entrega {@code payment_id} y {@code payment_url};</li>
 * <li>el cliente transfiere desde su banco y vuelve a {@code return_url} (o a {@code cancel_url});</li>
 * <li>{@code GET /v3/payments/{id}} confirma: {@code status = done} es pagado.</li>
 * </ol>
 * Se activa con {@code KHIPU_API_KEY}; sin ella, usa el simulador.
 */
@Component
public class KhipuProveedor implements ProveedorPago {

	private final RestClient client;
	private final String apiKey;
	private final String urlFrontend;

	public KhipuProveedor(@Qualifier("pasarelas") RestClient.Builder builder,
			@Value("${farmaexpress.pagos.khipu.url}") String url, @Value("${farmaexpress.pagos.khipu.api-key}") String apiKey,
			@Value("${farmaexpress.pagos.url-frontend}") String urlFrontend) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.client = builder.clone().baseUrl(url).defaultHeader("x-api-key", this.apiKey).build();
		this.urlFrontend = urlFrontend;
	}

	@Override
	public MetodoPago metodo() {
		return MetodoPago.KHIPU;
	}

	@Override
	public Pago.Modo modo() {
		return apiKey.isEmpty() ? Pago.Modo.SIMULADO : Pago.Modo.REAL;
	}

	record CobroRequest(int amount, String currency, String subject, String transaction_id, String return_url,
			String cancel_url) {
	}

	record CobroResponse(String payment_id, String payment_url) {
	}

	record EstadoResponse(String payment_id, String status, Integer amount, String transaction_id) {
	}

	@Override
	public Redireccion iniciar(Pago pago, String descripcion, String urlRetorno) {
		if (modo() == Pago.Modo.SIMULADO) {
			return Simulador.iniciar(pago, urlFrontend, urlRetorno);
		}
		CobroRequest request = new CobroRequest(pago.getMonto(), "CLP", descripcion, pago.ordenCompra(), urlRetorno,
				urlRetorno + "&cancelado=1");
		CobroResponse cobro = LlamadasHttp.llamar("Khipu", () -> client.post()
			.uri("/v3/payments")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.retrieve()
			.body(CobroResponse.class));
		if (cobro == null || cobro.payment_id() == null) {
			throw new ProveedorNoDisponibleException("Khipu no entregó un cobro");
		}
		return Redireccion.get(cobro.payment_url(), cobro.payment_id());
	}

	@Override
	public Resultado confirmar(Pago pago, Map<String, String> parametros) {
		if (modo() == Pago.Modo.SIMULADO) {
			return Simulador.confirmar(pago, parametros);
		}
		if (parametros.containsKey("cancelado")) {
			return Resultado.rechazado("Cancelaste la transferencia en Khipu.");
		}
		EstadoResponse estado = LlamadasHttp.llamar("Khipu", () -> client.get()
			.uri("/v3/payments/{id}", pago.getTokenProveedor())
			.retrieve()
			.body(EstadoResponse.class));
		if (estado == null) {
			throw new ProveedorNoDisponibleException("Khipu no informó el estado del cobro");
		}
		if ("done".equals(estado.status()) && Integer.valueOf(pago.getMonto()).equals(estado.amount())) {
			return Resultado.aprobado(estado.payment_id(), "Transferencia recibida por Khipu.");
		}
		if ("pending".equals(estado.status()) || "verifying".equals(estado.status())) {
			return Resultado.pendiente("Khipu está verificando tu transferencia.");
		}
		return Resultado.rechazado("Khipu no recibió la transferencia.");
	}
}
