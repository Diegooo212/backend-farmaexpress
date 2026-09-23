package cl.farmaexpress.bff;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import cl.farmaexpress.bff.catalog.CatalogClient;
import cl.farmaexpress.bff.catalog.CatalogClient.Medicamento;

/** Carrito y compras con el microservicio de catálogo simulado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@Transactional
class CarritoPedidoApiTests {

	private static final Medicamento PARACETAMOL = new Medicamento(1L, "MED-001", "Paracetamol 500mg", 1990, 120);
	private static final Medicamento IBUPROFENO = new Medicamento(2L, "MED-002", "Ibuprofeno 400mg", 2490, 8);
	private static final Medicamento AMOXICILINA = new Medicamento(3L, "MED-003", "Amoxicilina 500mg", 5990, 0);

	@Autowired
	MockMvc mvc;

	@MockitoBean
	CatalogClient catalog;

	@BeforeEach
	void catalogoSimulado() {
		given(catalog.listar()).willReturn(List.of(PARACETAMOL, IBUPROFENO, AMOXICILINA));
	}

	/** Access token de Entra ID de un cliente: oid, email, name y scope de la API. */
	private static RequestPostProcessor como(String email) {
		return jwt().jwt(j -> j.subject("sub-" + email).claim("oid", "oid-" + email).claim("email", email)
				.claim("name", email).claim("scp", "access_as_user").tokenValue("token-de-" + email))
			.authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"));
	}

	private void guardarCarrito(String email, String itemsJson) throws Exception {
		mvc.perform(put("/api/bff/cart").with(como(email)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":" + itemsJson + "}"))
			.andExpect(status().isOk());
	}

	@Test
	void carritoSeGuardaPorCuentaYSeAjustaAlStock() throws Exception {
		// Ibuprofeno pide 20 pero hay 8; Amoxicilina está agotada; el 99 no existe; Paracetamol viene repetido.
		mvc.perform(put("/api/bff/cart").with(como("diego@test.cl")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"id\":1,\"cantidad\":2},{\"id\":2,\"cantidad\":20},{\"id\":3,\"cantidad\":1},{\"id\":99,\"cantidad\":1},{\"id\":1,\"cantidad\":1}]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].nombre").value("Paracetamol 500mg"))
			.andExpect(jsonPath("$.items[0].cantidad").value(3))
			.andExpect(jsonPath("$.items[1].cantidad").value(8))
			.andExpect(jsonPath("$.items[1].stock").value(8))
			.andExpect(jsonPath("$.count").value(11))
			.andExpect(jsonPath("$.total").value(3 * 1990 + 8 * 2490));

		mvc.perform(get("/api/bff/cart").with(como("diego@test.cl"))).andExpect(jsonPath("$.items", hasSize(2)));
		mvc.perform(get("/api/bff/cart").with(como("otra@test.cl"))).andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void carritoRechazaCantidadesInvalidas() throws Exception {
		mvc.perform(put("/api/bff/cart").with(como("diego@test.cl")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"id\":1,\"cantidad\":0}]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("mayor que cero")));
	}

	@Test
	void comprarDescuentaStockGuardaPedidoYVaciaCarrito() throws Exception {
		guardarCarrito("diego@test.cl", "[{\"id\":1,\"cantidad\":2},{\"id\":2,\"cantidad\":1}]");
		given(catalog.descontarStock(anyList(), eq("token-de-diego@test.cl"))).willReturn(List.of(
				new Medicamento(1L, "MED-001", "Paracetamol 500mg", 1990, 118),
				new Medicamento(2L, "MED-002", "Ibuprofeno 400mg", 2490, 7)));

		mvc.perform(post("/api/bff/orders").with(como("diego@test.cl")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.total").value(2 * 1990 + 2490))
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].nombre").value("Paracetamol 500mg"));

		mvc.perform(get("/api/bff/cart").with(como("diego@test.cl"))).andExpect(jsonPath("$.items", hasSize(0)));
		mvc.perform(get("/api/bff/orders").with(como("diego@test.cl"))).andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(get("/api/bff/orders").with(como("otra@test.cl"))).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void comprarConCarritoVacioDa400() throws Exception {
		mvc.perform(post("/api/bff/orders").with(como("diego@test.cl")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Tu carrito está vacío."));
		verify(catalog, never()).descontarStock(anyList(), any());
	}

	@Test
	void sinStockSeDevuelveElMensajeDelCatalogoYElCarritoQueda() throws Exception {
		guardarCarrito("diego@test.cl", "[{\"id\":2,\"cantidad\":8}]");
		String problema = "{\"status\":409,\"detail\":\"No hay stock suficiente de Ibuprofeno 400mg: quedan 3 y pediste 8.\"}";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		given(catalog.descontarStock(anyList(), any())).willThrow(HttpClientErrorException.create(HttpStatus.CONFLICT,
				"Conflict", headers, problema.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

		mvc.perform(post("/api/bff/orders").with(como("diego@test.cl")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("quedan 3")));
		mvc.perform(get("/api/bff/cart").with(como("diego@test.cl"))).andExpect(jsonPath("$.items", hasSize(1)));
	}

	@Test
	void catalogoCaidoDa503() throws Exception {
		given(catalog.listar()).willThrow(new ResourceAccessException("Connection refused"));
		mvc.perform(get("/api/bff/cart").with(como("diego@test.cl")))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.detail").value(containsString("no está disponible")));
	}

	@Test
	void catalogoPublicoSeReenviaYEditarExigeAdmin() throws Exception {
		given(catalog.reenviar(HttpMethod.GET, "", null, null)).willReturn(ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_JSON).body("[{\"id\":1}]".getBytes(StandardCharsets.UTF_8)));

		mvc.perform(get("/api/bff/catalog/medicamentos"))
			.andExpect(status().isOk())
			.andExpect(content().json("[{\"id\":1}]"));
		mvc.perform(post("/api/bff/catalog/medicamentos").with(como("diego@test.cl"))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());
	}
}
