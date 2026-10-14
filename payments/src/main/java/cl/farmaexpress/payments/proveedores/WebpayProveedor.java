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
 * Webpay Plus de Transbank (API REST v1.2).
 * <ol>
 * <li>{@code POST /transactions} crea la transacción y entrega {@code token} y {@code url};</li>
 * <li>el navegador envía {@code token_ws} a esa URL por formulario POST y el cliente paga;</li>
 * <li>Webpay devuelve al cliente a nuestra URL de retorno con {@code token_ws} (pagó o fue rechazado),
 * {@code TBK_TOKEN} (anuló) o solo {@code TBK_ORDEN_COMPRA} (se le acabó el tiempo);</li>
 * <li>{@code PUT /transactions/{token}} confirma ("commit"): {@code response_code = 0} y
 * {@code status = AUTHORIZED} significa aprobado.</li>
 * </ol>
 * Se activa con {@code WEBPAY_CODIGO_COMERCIO} y {@code WEBPAY_API_KEY}; sin ellas, usa el simulador
 * (igual que Mercado Pago y Khipu).
 */
@Component
public class WebpayProveedor implements ProveedorPago {

	private static final String TRANSACCIONES = "/rswebpaytransaction/api/webpay/v1.2/transactions";

	private final RestClient client;
	private final String apiKey;
	private final String urlFrontend;

	public WebpayProveedor(@Qualifier("pasarelas") RestClient.Builder builder, @Value("${farmaexpress.pagos.webpay.url}") String url,
			@Value("${farmaexpress.pagos.webpay.codigo-comercio}") String codigoComercio,
			@Value("${farmaexpress.pagos.webpay.api-key}") String apiKey,
			@Value("${farmaexpress.pagos.url-frontend}") String urlFrontend) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.client = builder.clone()
			.baseUrl(url)
			.defaultHeader("Tbk-Api-Key-Id", codigoComercio == null ? "" : codigoComercio.trim())
			.defaultHeader("Tbk-Api-Key-Secret", this.apiKey)
			.build();
		this.urlFrontend = urlFrontend;
	}

	@Override
	public MetodoPago metodo() {
		return MetodoPago.WEBPAY;
	}

	@Override
	public Pago.Modo modo() {
		return apiKey.isEmpty() ? Pago.Modo.SIMULADO : Pago.Modo.REAL;
	}

	record CrearRequest(String buy_order, String session_id, int amount, String return_url) {
	}

	record CrearResponse(String token, String url) {
	}

	record CardDetail(String card_number) {
	}

	record CommitResponse(String vci, Integer amount, String status, String buy_order, String authorization_code,
			Integer response_code, String payment_type_code, Integer installments_number, CardDetail card_detail) {
	}

	@Override
	public Redireccion iniciar(Pago pago, String descripcion, String urlRetorno) {
		if (modo() == Pago.Modo.SIMULADO) {
			return Simulador.iniciar(pago, urlFrontend, urlRetorno);
		}
		CrearResponse respuesta = LlamadasHttp.llamar("Webpay", () -> client.post()
			.uri(TRANSACCIONES)
			.contentType(MediaType.APPLICATION_JSON)
			.body(new CrearRequest(pago.ordenCompra(), "S" + pago.getId(), pago.getMonto(), urlRetorno))
			.retrieve()
			.body(CrearResponse.class));
		if (respuesta == null || respuesta.token() == null) {
			throw new ProveedorNoDisponibleException("Webpay no entregó un token");
		}
		// Webpay exige que el navegador envíe el token por formulario POST.
		return new Redireccion("POST", respuesta.url(), Map.of("token_ws", respuesta.token()), respuesta.token());
	}

	@Override
	public Resultado confirmar(Pago pago, Map<String, String> parametros) {
		if (modo() == Pago.Modo.SIMULADO) {
			return Simulador.confirmar(pago, parametros);
		}
		String tokenWs = parametros.get("token_ws");
		if (tokenWs == null || tokenWs.isBlank()) {
			return parametros.containsKey("TBK_TOKEN") ? Resultado.rechazado("Anulaste el pago en Webpay.")
					: Resultado.rechazado("Se agotó el tiempo para pagar en Webpay.");
		}
		if (!tokenWs.equals(pago.getTokenProveedor())) {
			throw new IllegalArgumentException("El token de Webpay no corresponde al pago " + pago.getId());
		}
		CommitResponse commit = LlamadasHttp.llamar("Webpay",
				() -> client.put().uri(TRANSACCIONES + "/{token}", tokenWs).retrieve().body(CommitResponse.class));
		if (commit != null && Integer.valueOf(0).equals(commit.response_code()) && "AUTHORIZED".equals(commit.status())
				&& Integer.valueOf(pago.getMonto()).equals(commit.amount())) {
			String tarjeta = commit.card_detail() != null && commit.card_detail().card_number() != null
					? " con la tarjeta terminada en " + commit.card_detail().card_number() : "";
			return Resultado.aprobado(commit.authorization_code(), "Pago aprobado en Webpay" + tarjeta + ".");
		}
		return Resultado.rechazado("Webpay rechazó el pago" + (commit != null && commit.response_code() != null
				? " (código " + commit.response_code() + ")" : "") + ". No se hizo ningún cobro.");
	}
}
