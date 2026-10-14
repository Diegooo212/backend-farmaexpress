package cl.farmaexpress.bff;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import com.jayway.jsonpath.JsonPath;

import cl.farmaexpress.bff.catalog.CatalogClient;
import cl.farmaexpress.bff.catalog.CatalogClient.Medicamento;
import cl.farmaexpress.bff.eventos.EventosListener;
import cl.farmaexpress.bff.eventos.EventosListener.PagoAprobado;
import cl.farmaexpress.bff.eventos.EventosListener.PagoRechazado;
import cl.farmaexpress.bff.eventos.EventosListener.ReservaExpirada;
import cl.farmaexpress.bff.inventory.InventoryClient;
import cl.farmaexpress.bff.inventory.InventoryClient.Reserva;
import cl.farmaexpress.bff.inventory.InventoryClient.Sucursal;
import cl.farmaexpress.bff.payments.PaymentsClient;
import cl.farmaexpress.bff.payments.PaymentsClient.PagoIniciado;
import cl.farmaexpress.bff.payments.PaymentsClient.Redireccion;
import cl.farmaexpress.bff.prescriptions.PrescriptionsClient;
import cl.farmaexpress.bff.prescriptions.PrescriptionsClient.Receta;

/**
 * Checkout: carrito → reserva de stock (inventory, FEFO) → inicio del pago (payments) → resultado por
 * eventos de RabbitMQ. Inventory y payments están simulados; los eventos se entregan al listener.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@Transactional
class CheckoutApiTests {

	private static final Medicamento PARACETAMOL = new Medicamento(1L, "MED-001", "Paracetamol 500mg", 1990, 120);
	private static final Medicamento IBUPROFENO = new Medicamento(2L, "MED-002", "Ibuprofeno 400mg", 2490, 8);
	private static final Medicamento AMOXICILINA = new Medicamento(3L, "MED-003", "Amoxicilina 500mg", 5990, 20, true);
	private static final Sucursal CENTRO = new Sucursal(2L, "CENTRO", "Farmacia Centro", "Av. Libertador 1200", "Santiago", "FARMACIA");
	private static final Sucursal CENTRAL = new Sucursal(1L, "CENTRAL", "Almacén Central", "Av. Vespucio 1500", "Pudahuel", "CENTRAL");

	@Autowired
	MockMvc mvc;

	@Autowired
	EventosListener eventos;

	@MockitoBean
	CatalogClient catalog;

	@MockitoBean
	InventoryClient inventory;

	@MockitoBean
	PaymentsClient payments;

	@MockitoBean
	PrescriptionsClient prescriptions;

	@BeforeEach
	void servicios() {
		given(catalog.listar()).willReturn(List.of(PARACETAMOL, IBUPROFENO, AMOXICILINA));
		String expira = Instant.now().plusSeconds(900).toString();
		given(inventory.reservar(anyString(), eq(2L), anyList(), anyString()))
			.willAnswer(i -> new Reserva(10L, i.getArgument(0), "ACTIVA", CENTRO, expira));
		given(inventory.reservar(anyString(), isNull(), anyList(), anyString()))
			.willAnswer(i -> new Reserva(11L, i.getArgument(0), "ACTIVA", CENTRAL, expira));
		given(payments.iniciar(anyLong(), anyLong(), anyInt(), anyString(), anyString(), anyString()))
			.willAnswer(i -> new PagoIniciado(500L, i.getArgument(0), "PENDIENTE", i.getArgument(3), "REAL",
					new Redireccion("POST", "https://webpay3gint.transbank.cl/webpayserver/initTransaction",
							Map.of("token_ws", "tok-123"))));
	}

	private static RequestPostProcessor como(String email) {
		return jwt().jwt(j -> j.subject("sub-" + email).claim("oid", "oid-" + email).claim("email", email)
				.claim("name", email).claim("scp", "access_as_user").tokenValue("token-de-" + email))
			.authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"));
	}

	private static final RequestPostProcessor DIEGO = como("diego@test.cl");

	private void carrito(String itemsJson) throws Exception {
		mvc.perform(put("/api/bff/cart").with(DIEGO).contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":" + itemsJson + "}"))
			.andExpect(status().isOk());
	}

	private ResultActions checkout(String body) throws Exception {
		return mvc.perform(post("/api/bff/orders").with(DIEGO).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private long pedidoId(ResultActions r) throws Exception {
		return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.pedido.id")).longValue();
	}

	private static final String RETIRO_WEBPAY = "{\"entrega\":\"RETIRO\",\"sucursalId\":2,\"metodoPago\":\"WEBPAY\"}";

	@Test
	void retiroEnFarmaciaReservaElStockEIniciaElPago() throws Exception {
		carrito("[{\"id\":1,\"cantidad\":2},{\"id\":2,\"cantidad\":1}]");

		long id = pedidoId(checkout(RETIRO_WEBPAY)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.pedido.estado").value("PENDIENTE_PAGO"))
			.andExpect(jsonPath("$.pedido.entrega").value("RETIRO"))
			.andExpect(jsonPath("$.pedido.sucursal").value("Farmacia Centro"))
			.andExpect(jsonPath("$.pedido.costoDespacho").value(0))
			.andExpect(jsonPath("$.pedido.total").value(2 * 1990 + 2490))
			.andExpect(jsonPath("$.pago.pagoId").value(500))
			.andExpect(jsonPath("$.pago.redireccion.metodo").value("POST"))
			.andExpect(jsonPath("$.pago.redireccion.campos.token_ws").value("tok-123")));

		verify(inventory).reservar(eq("PED-" + id), eq(2L), anyList(), eq("token-de-diego@test.cl"));
		verify(payments).iniciar(eq(id), eq(10L), eq(2 * 1990 + 2490), eq("WEBPAY"), anyString(), eq("token-de-diego@test.cl"));
		// El carrito se mantiene hasta que el pago se apruebe.
		mvc.perform(get("/api/bff/cart").with(DIEGO)).andExpect(jsonPath("$.items", hasSize(2)));
	}

	@Test
	void elPagoAprobadoPorRabbitMqMarcaElPedidoPagadoYVaciaElCarrito() throws Exception {
		carrito("[{\"id\":1,\"cantidad\":2}]");
		long id = pedidoId(checkout(RETIRO_WEBPAY));

		eventos.pagoAprobado(new PagoAprobado("ev-1", 500L, id, 10L, "oid-diego@test.cl", 3980, "WEBPAY", "1213", Instant.now()));

		mvc.perform(get("/api/bff/orders/" + id).with(DIEGO))
			.andExpect(jsonPath("$.estado").value("PAGADO"))
			.andExpect(jsonPath("$.metodoPago").value("WEBPAY"));
		mvc.perform(get("/api/bff/cart").with(DIEGO)).andExpect(jsonPath("$.items", hasSize(0)));
		mvc.perform(get("/api/bff/orders").with(DIEGO)).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void despachoADomicilioSaleDelAlmacenCentralYCobraDespachoBajoElMinimo() throws Exception {
		carrito("[{\"id\":1,\"cantidad\":1}]");

		checkout("{\"entrega\":\"DESPACHO\",\"direccion\":\"Av. Siempre Viva 742, Santiago\",\"metodoPago\":\"KHIPU\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.pedido.sucursal").value("Almacén Central"))
			.andExpect(jsonPath("$.pedido.direccion").value("Av. Siempre Viva 742, Santiago"))
			.andExpect(jsonPath("$.pedido.subtotal").value(1990))
			.andExpect(jsonPath("$.pedido.costoDespacho").value(2990))
			.andExpect(jsonPath("$.pedido.total").value(1990 + 2990));
		verify(inventory).reservar(anyString(), isNull(), anyList(), anyString());
	}

	@Test
	void datosDeEntregaIncompletosDan400() throws Exception {
		carrito("[{\"id\":1,\"cantidad\":1}]");
		checkout("{\"entrega\":\"RETIRO\",\"metodoPago\":\"WEBPAY\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("farmacia")));
		checkout("{\"entrega\":\"DESPACHO\",\"direccion\":\"\",\"metodoPago\":\"WEBPAY\"}").andExpect(status().isBadRequest());
		checkout("{\"entrega\":\"RETIRO\",\"sucursalId\":2,\"metodoPago\":\"PAYPAL\"}").andExpect(status().isBadRequest());
	}

	@Test
	void conCarritoVacioDa400() throws Exception {
		checkout(RETIRO_WEBPAY).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Tu carrito está vacío."));
		verify(inventory, never()).reservar(anyString(), any(), anyList(), anyString());
	}

	@Test
	void sinStockEnLaFarmaciaNoSeCreaElPedido() throws Exception {
		carrito("[{\"id\":2,\"cantidad\":8}]");
		String problema = "{\"status\":409,\"detail\":\"No hay stock suficiente de Ibuprofeno 400mg en Farmacia Centro: quedan 3 y pediste 8.\"}";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		given(inventory.reservar(anyString(), eq(2L), anyList(), anyString())).willThrow(HttpClientErrorException.create(
				HttpStatus.CONFLICT, "Conflict", headers, problema.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

		checkout(RETIRO_WEBPAY)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("quedan 3")));
		mvc.perform(get("/api/bff/orders").with(DIEGO)).andExpect(jsonPath("$", hasSize(0)));
		verify(payments, never()).iniciar(anyLong(), anyLong(), anyInt(), anyString(), anyString(), anyString());
	}

	@Test
	void siElProveedorEstaCaidoElPedidoEsperaYSePagaConOtroMedio() throws Exception {
		carrito("[{\"id\":1,\"cantidad\":1}]");
		String problema = "{\"status\":503,\"detail\":\"Khipu no está disponible en este momento. Elige otro medio de pago.\"}";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		given(payments.iniciar(anyLong(), anyLong(), anyInt(), eq("KHIPU"), anyString(), anyString()))
			.willThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", headers,
					problema.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

		long id = pedidoId(checkout("{\"entrega\":\"RETIRO\",\"sucursalId\":2,\"metodoPago\":\"KHIPU\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.pedido.estado").value("PENDIENTE_PAGO"))
			.andExpect(jsonPath("$.pago.redireccion").value(nullValue()))
			.andExpect(jsonPath("$.pago.error").value(containsString("Khipu no está disponible"))));

		mvc.perform(post("/api/bff/orders/" + id + "/pago").with(DIEGO).contentType(MediaType.APPLICATION_JSON)
				.content("{\"metodoPago\":\"WEBPAY\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pago.redireccion.url").value(containsString("webpay")));
	}

	@Test
	void unPagoRechazadoSePuedeReintentarPeroNoSiLaReservaExpiro() throws Exception {
		carrito("[{\"id\":1,\"cantidad\":1}]");
		long id = pedidoId(checkout(RETIRO_WEBPAY));

		eventos.pagoRechazado(new PagoRechazado("ev-2", 500L, id, 10L, "oid", 1990, "WEBPAY", "Webpay rechazó el pago.", Instant.now()));
		mvc.perform(get("/api/bff/orders/" + id).with(DIEGO))
			.andExpect(jsonPath("$.estado").value("PAGO_RECHAZADO"))
			.andExpect(jsonPath("$.detallePago").value("Webpay rechazó el pago."));
		mvc.perform(post("/api/bff/orders/" + id + "/pago").with(DIEGO).contentType(MediaType.APPLICATION_JSON)
				.content("{\"metodoPago\":\"MERCADOPAGO\"}"))
			.andExpect(status().isOk());

		eventos.reservaExpirada(new ReservaExpirada("ev-3", 10L, "PED-" + id, Instant.now()));
		mvc.perform(get("/api/bff/orders/" + id).with(DIEGO)).andExpect(jsonPath("$.estado").value("EXPIRADO"));
		mvc.perform(post("/api/bff/orders/" + id + "/pago").with(DIEGO).contentType(MediaType.APPLICATION_JSON)
				.content("{\"metodoPago\":\"WEBPAY\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	void cancelarLiberaLaReserva() throws Exception {
		carrito("[{\"id\":1,\"cantidad\":1}]");
		long id = pedidoId(checkout(RETIRO_WEBPAY));

		mvc.perform(delete("/api/bff/orders/" + id).with(DIEGO))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.estado").value("CANCELADO"));
		verify(inventory).liberar(10L, "token-de-diego@test.cl");
		mvc.perform(delete("/api/bff/orders/" + id).with(DIEGO)).andExpect(status().isConflict());
	}

	@Test
	void unPedidoAjenoNoSeVe() throws Exception {
		carrito("[{\"id\":1,\"cantidad\":1}]");
		long id = pedidoId(checkout(RETIRO_WEBPAY));
		mvc.perform(get("/api/bff/orders/" + id).with(como("otra@test.cl"))).andExpect(status().isNotFound());
	}

	@Test
	void unProductoConRecetaExigeElegirUnaReceta() throws Exception {
		carrito("[{\"id\":3,\"cantidad\":1},{\"id\":1,\"cantidad\":1}]");

		checkout(RETIRO_WEBPAY)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("venta bajo receta (Amoxicilina 500mg)")));
		verify(inventory, never()).reservar(anyString(), any(), anyList(), anyString());
	}

	@Test
	void unaRecetaSinValidarNoSirve() throws Exception {
		carrito("[{\"id\":3,\"cantidad\":1}]");
		given(prescriptions.obtener(7L, "token-de-diego@test.cl")).willReturn(new Receta(7L, "INGRESADA", "Diego"));

		checkout("{\"entrega\":\"RETIRO\",\"sucursalId\":2,\"metodoPago\":\"WEBPAY\",\"recetaId\":7}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("aún no ha sido validada")));
	}

	@Test
	void unaRecetaValidadaRespaldaUnSoloPedido() throws Exception {
		carrito("[{\"id\":3,\"cantidad\":1}]");
		given(prescriptions.obtener(8L, "token-de-diego@test.cl")).willReturn(new Receta(8L, "VALIDADA", "Diego"));
		String conReceta = "{\"entrega\":\"RETIRO\",\"sucursalId\":2,\"metodoPago\":\"WEBPAY\",\"recetaId\":8}";

		checkout(conReceta)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.pedido.recetaId").value(8));
		// La misma receta no sirve para otra compra mientras ese pedido siga vigente.
		checkout(conReceta)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("ya respalda otra compra")));
	}

	@Test
	void unaRecetaDeOtraCuentaNoSeEncuentra() throws Exception {
		carrito("[{\"id\":3,\"cantidad\":1}]");
		given(prescriptions.obtener(9L, "token-de-diego@test.cl"))
			.willThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

		checkout("{\"entrega\":\"RETIRO\",\"sucursalId\":2,\"metodoPago\":\"WEBPAY\",\"recetaId\":9}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("No encontramos esa receta en tu cuenta."));
	}
}
