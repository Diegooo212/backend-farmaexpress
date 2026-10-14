package cl.farmaexpress.payments.proveedores;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import cl.farmaexpress.payments.domain.MetodoPago;
import cl.farmaexpress.payments.domain.Pago;

/**
 * Mercado Pago Checkout Pro.
 * <ol>
 * <li>{@code POST /checkout/preferences} crea la preferencia y entrega {@code init_point}
 * ({@code sandbox_init_point} con credenciales de prueba);</li>
 * <li>el cliente paga y vuelve a la {@code back_url} con {@code payment_id} y {@code status};</li>
 * <li>{@code GET /v1/payments/{id}} confirma el estado real ({@code approved}), la referencia y el monto.</li>
 * </ol>
 * Se activa con {@code MERCADOPAGO_ACCESS_TOKEN}; sin él, usa el simulador.
 */
@Component
public class MercadoPagoProveedor implements ProveedorPago {

	private final RestClient client;
	private final String accessToken;
	private final String urlFrontend;

	public MercadoPagoProveedor(@Qualifier("pasarelas") RestClient.Builder builder,
			@Value("${farmaexpress.pagos.mercadopago.url}") String url,
			@Value("${farmaexpress.pagos.mercadopago.access-token}") String accessToken,
			@Value("${farmaexpress.pagos.url-frontend}") String urlFrontend) {
		this.accessToken = accessToken == null ? "" : accessToken.trim();
		this.client = builder.clone().baseUrl(url).defaultHeader("Authorization", "Bearer " + this.accessToken).build();
		this.urlFrontend = urlFrontend;
	}

	@Override
	public MetodoPago metodo() {
		return MetodoPago.MERCADOPAGO;
	}

	@Override
	public Pago.Modo modo() {
		return accessToken.isEmpty() ? Pago.Modo.SIMULADO : Pago.Modo.REAL;
	}

	record Item(String title, int quantity, int unit_price, String currency_id) {
	}

	record BackUrls(String success, String failure, String pending) {
	}

	record PreferenciaRequest(List<Item> items, String external_reference, BackUrls back_urls) {
	}

	record PreferenciaResponse(String id, String init_point, String sandbox_init_point) {
	}

	record PagoResponse(Long id, String status, String status_detail, String external_reference,
			Double transaction_amount) {
	}

	@Override
	public Redireccion iniciar(Pago pago, String descripcion, String urlRetorno) {
		if (modo() == Pago.Modo.SIMULADO) {
			return Simulador.iniciar(pago, urlFrontend, urlRetorno);
		}
		PreferenciaRequest request = new PreferenciaRequest(
				List.of(new Item(descripcion, 1, pago.getMonto(), "CLP")), pago.ordenCompra(),
				new BackUrls(urlRetorno, urlRetorno, urlRetorno));
		PreferenciaResponse preferencia = LlamadasHttp.llamar("Mercado Pago", () -> client.post()
			.uri("/checkout/preferences")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.retrieve()
			.body(PreferenciaResponse.class));
		if (preferencia == null || preferencia.id() == null) {
			throw new ProveedorNoDisponibleException("Mercado Pago no entregó una preferencia");
		}
		// Las credenciales de prueba empiezan con TEST- y pagan en el sandbox.
		String url = accessToken.startsWith("TEST-") && preferencia.sandbox_init_point() != null
				? preferencia.sandbox_init_point() : preferencia.init_point();
		return Redireccion.get(url, preferencia.id());
	}

	@Override
	public Resultado confirmar(Pago pago, Map<String, String> parametros) {
		if (modo() == Pago.Modo.SIMULADO) {
			return Simulador.confirmar(pago, parametros);
		}
		String paymentId = parametros.getOrDefault("payment_id", parametros.get("collection_id"));
		if (paymentId == null || paymentId.isBlank() || "null".equals(paymentId)) {
			return Resultado.rechazado("No completaste el pago en Mercado Pago.");
		}
		PagoResponse mp = LlamadasHttp.llamar("Mercado Pago",
				() -> client.get().uri("/v1/payments/{id}", paymentId).retrieve().body(PagoResponse.class));
		if (mp == null || !pago.ordenCompra().equals(mp.external_reference())) {
			throw new IllegalArgumentException("El pago de Mercado Pago " + paymentId + " no corresponde al pago " + pago.getId());
		}
		boolean montoCorrecto = mp.transaction_amount() != null && mp.transaction_amount().intValue() == pago.getMonto();
		if ("approved".equals(mp.status()) && montoCorrecto) {
			return Resultado.aprobado(String.valueOf(mp.id()), "Pago aprobado en Mercado Pago.");
		}
		if ("pending".equals(mp.status()) || "in_process".equals(mp.status())) {
			return Resultado.pendiente("Mercado Pago está procesando tu pago.");
		}
		return Resultado.rechazado("Mercado Pago rechazó el pago (" + mp.status_detail() + ").");
	}
}
