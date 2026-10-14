package cl.farmaexpress.payments.proveedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import cl.farmaexpress.payments.domain.MetodoPago;
import cl.farmaexpress.payments.domain.Pago;
import cl.farmaexpress.payments.proveedores.ProveedorPago.Redireccion;
import cl.farmaexpress.payments.proveedores.ProveedorPago.Resultado;

/**
 * Contrato con la API REST de Webpay Plus v1.2 (Transbank): headers, cuerpo y respuestas, contra un
 * servidor HTTP simulado.
 */
class WebpayProveedorTests {

	private static final String BASE = "https://webpay3gint.transbank.cl/rswebpaytransaction/api/webpay/v1.2/transactions";

	private MockRestServiceServer servidor;
	private WebpayProveedor webpay;
	private Pago pago;

	@BeforeEach
	void preparar() {
		RestClient.Builder builder = RestClient.builder();
		servidor = MockRestServiceServer.bindTo(builder).build();
		webpay = new WebpayProveedor(builder, "https://webpay3gint.transbank.cl", "597055555532", "LLAVE",
				"http://localhost:5173");
		pago = new Pago(55L, 7L, "oid-cliente", 5980, MetodoPago.WEBPAY, Pago.Modo.REAL, "sim", Instant.now());
		ReflectionTestUtils.setField(pago, "id", 42L);
	}

	@Test
	void crearTransaccionYEnviarElTokenPorPost() {
		servidor.expect(requestTo(BASE))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Tbk-Api-Key-Id", "597055555532"))
			.andExpect(header("Tbk-Api-Key-Secret", "LLAVE"))
			.andExpect(content().json("""
					{"buy_order":"FX42","session_id":"S42","amount":5980,
					 "return_url":"http://localhost:8080/api/bff/payments/retorno/webpay?pago=42"}"""))
			.andRespond(withSuccess("{\"token\":\"tok123\",\"url\":\"https://webpay3gint.transbank.cl/webpayserver/initTransaction\"}",
					MediaType.APPLICATION_JSON));

		Redireccion r = webpay.iniciar(pago, "Pedido", "http://localhost:8080/api/bff/payments/retorno/webpay?pago=42");

		assertThat(r.metodoHttp()).isEqualTo("POST");
		assertThat(r.url()).endsWith("/initTransaction");
		assertThat(r.campos()).containsEntry("token_ws", "tok123");
		assertThat(r.tokenProveedor()).isEqualTo("tok123");
		servidor.verify();
	}

	@Test
	void commitAutorizadoEsAprobado() {
		pago.iniciado("tok123", Instant.now());
		servidor.expect(requestTo(BASE + "/tok123"))
			.andExpect(method(HttpMethod.PUT))
			.andRespond(withSuccess("""
					{"vci":"TSY","amount":5980,"status":"AUTHORIZED","buy_order":"FX42","authorization_code":"1213",
					 "response_code":0,"payment_type_code":"VN","installments_number":0,"card_detail":{"card_number":"6623"}}""",
					MediaType.APPLICATION_JSON));

		Resultado r = webpay.confirmar(pago, Map.of("pago", "42", "token_ws", "tok123"));

		assertThat(r.aprobado()).isTrue();
		assertThat(r.referencia()).isEqualTo("1213");
		assertThat(r.detalle()).contains("6623");
	}

	@Test
	void commitConCodigoDistintoDeCeroEsRechazado() {
		pago.iniciado("tok123", Instant.now());
		servidor.expect(requestTo(BASE + "/tok123"))
			.andRespond(withSuccess("{\"amount\":5980,\"status\":\"FAILED\",\"response_code\":-1}", MediaType.APPLICATION_JSON));

		assertThat(webpay.confirmar(pago, Map.of("token_ws", "tok123")).aprobado()).isFalse();
	}

	@Test
	void siElClienteAnulaNoSeLlamaAWebpay() {
		Resultado r = webpay.confirmar(pago, Map.of("TBK_TOKEN", "tok123", "TBK_ORDEN_COMPRA", "FX42"));

		assertThat(r.aprobado()).isFalse();
		assertThat(r.detalle()).contains("Anulaste");
		servidor.verify();
	}

	@Test
	void unTokenQueNoEsDelPagoSeRechazaSinConfirmar() {
		pago.iniciado("tok123", Instant.now());
		assertThatThrownBy(() -> webpay.confirmar(pago, Map.of("token_ws", "otro")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void webpayCaidoEsUnaFallaParaElCircuitBreaker() {
		servidor.expect(requestTo(BASE)).andRespond(withServiceUnavailable());

		assertThatThrownBy(() -> webpay.iniciar(pago, "Pedido", "http://x"))
			.isInstanceOf(ProveedorNoDisponibleException.class);
	}
}
