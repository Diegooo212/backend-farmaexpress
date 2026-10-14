package cl.farmaexpress.inventory;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import cl.farmaexpress.inventory.eventos.Eventos.PagoAprobado;
import cl.farmaexpress.inventory.eventos.EventosListener;
import cl.farmaexpress.inventory.service.InventarioService;

/**
 * Inventario con la carga inicial (V2__inventario_inicial.sql) y "hoy" = 29-09-2026.
 * Sucursales: 1 Almacén Central, 2 Farmacia Centro, 3 Farmacia Norte, 4 Farmacia Sur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@Import({ EntraDePrueba.class, RelojDePrueba.class })
@Transactional
class InventarioApiTests {

	private static final String BASE = "/api/inventory";

	@Autowired
	MockMvc mvc;

	@Autowired
	EventosListener listener;

	@Autowired
	InventarioService inventario;

	@Autowired
	JdbcTemplate jdbc;

	private static RequestPostProcessor usuario(String oid, String rol) {
		return jwt().jwt(j -> j.subject("sub-" + oid).claim("oid", oid).claim("name", "Usuario " + oid)
				.claim("scp", "access_as_user").claim("roles", List.of(rol)))
			.authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"), new SimpleGrantedAuthority("ROLE_" + rol));
	}

	private static final RequestPostProcessor CLIENTE = usuario("oid-cliente", "Cliente");
	private static final RequestPostProcessor OTRO_CLIENTE = usuario("oid-otro", "Cliente");
	private static final RequestPostProcessor OPERADOR = usuario("oid-operador", "Operador");

	private ResultActions reservar(RequestPostProcessor quien, String sucursalId, String items) throws Exception {
		String body = "{\"referencia\":\"PED-1\",\"sucursalId\":%s,\"items\":%s}".formatted(sucursalId, items);
		return mvc.perform(post(BASE + "/reservas").with(quien).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private long crearReserva(String sucursalId, String items) throws Exception {
		String json = reservar(CLIENTE, sucursalId, items).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	private int disponibleDelLote(String numeroLote, long sucursalId) {
		return jdbc.queryForObject("select disponible from lotes where numero_lote = ? and sucursal_id = ?", Integer.class,
				numeroLote, sucursalId);
	}

	@Test
	void sucursalesYDisponibilidadSonPublicasYExcluyenLotesVencidos() throws Exception {
		mvc.perform(get(BASE + "/sucursales"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(4)))
			.andExpect(jsonPath("$[0].tipo").value("CENTRAL"));

		// Paracetamol en Farmacia Sur: 10 vendibles; el lote PAR-2312-Z (6 unidades) venció el 31-08.
		mvc.perform(get(BASE + "/disponibilidad/1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].disponible").value(80))
			.andExpect(jsonPath("$[1].disponible").value(20))
			.andExpect(jsonPath("$[3].disponible").value(10));
	}

	@Test
	void fefoTomaPrimeroElLoteQueVenceAntes() throws Exception {
		// Almacén central: PAR-2408-A vence el 15-11-2026 (30 u.) y PAR-2502-B el 31-08-2027 (50 u.).
		reservar(CLIENTE, "null", "[{\"medicamentoId\":1,\"cantidad\":35}]")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.estado").value("ACTIVA"))
			.andExpect(jsonPath("$.sucursal.tipo").value("CENTRAL"))
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].numeroLote").value("PAR-2408-A"))
			.andExpect(jsonPath("$.items[0].cantidad").value(30))
			.andExpect(jsonPath("$.items[1].numeroLote").value("PAR-2502-B"))
			.andExpect(jsonPath("$.items[1].cantidad").value(5));

		org.assertj.core.api.Assertions.assertThat(disponibleDelLote("PAR-2408-A", 1)).isZero();
		org.assertj.core.api.Assertions.assertThat(disponibleDelLote("PAR-2502-B", 1)).isEqualTo(45);
	}

	@Test
	void unLoteVencidoNuncaSeVende() throws Exception {
		// Farmacia Sur tiene 16 unidades físicas de Paracetamol, pero 6 están vencidas.
		reservar(CLIENTE, "4", "[{\"medicamentoId\":1,\"cantidad\":11,\"nombre\":\"Paracetamol 500mg\"}]")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("Paracetamol 500mg en Farmacia Sur: quedan 10")));
	}

	@Test
	void laReservaEsTodoONada() throws Exception {
		reservar(CLIENTE, "null", "[{\"medicamentoId\":1,\"cantidad\":5},{\"medicamentoId\":2,\"cantidad\":99}]")
			.andExpect(status().isConflict());

		org.assertj.core.api.Assertions.assertThat(disponibleDelLote("PAR-2408-A", 1)).isEqualTo(30);
	}

	@Test
	void cancelarLiberaLasUnidades() throws Exception {
		long id = crearReserva("2", "[{\"medicamentoId\":1,\"cantidad\":4}]");
		org.assertj.core.api.Assertions.assertThat(disponibleDelLote("PAR-2408-A", 2)).isEqualTo(16);

		mvc.perform(delete(BASE + "/reservas/" + id).with(CLIENTE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.estado").value("LIBERADA"));
		org.assertj.core.api.Assertions.assertThat(disponibleDelLote("PAR-2408-A", 2)).isEqualTo(20);
	}

	@Test
	void unaReservaAjenaNoSeVeNiSeCancela() throws Exception {
		long id = crearReserva("2", "[{\"medicamentoId\":1,\"cantidad\":1}]");

		mvc.perform(get(BASE + "/reservas/" + id).with(OTRO_CLIENTE)).andExpect(status().isNotFound());
		mvc.perform(delete(BASE + "/reservas/" + id).with(OTRO_CLIENTE)).andExpect(status().isNotFound());
		mvc.perform(get(BASE + "/reservas/" + id).with(OPERADOR)).andExpect(status().isOk());
	}

	@Test
	void elPagoAprobadoConfirmaLaSalidaUnaSolaVez() throws Exception {
		long id = crearReserva("2", "[{\"medicamentoId\":5,\"cantidad\":3}]");
		PagoAprobado pago = new PagoAprobado("ev-1", 9L, 1L, id, "oid-cliente", 12570, "WEBPAY", Instant.now());

		listener.pagoAprobado(pago);
		listener.pagoAprobado(pago);

		mvc.perform(get(BASE + "/reservas/" + id).with(CLIENTE)).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
		org.assertj.core.api.Assertions.assertThat(disponibleDelLote("OME-2502-A", 2)).isEqualTo(2);
		org.assertj.core.api.Assertions.assertThat(
				jdbc.queryForObject("select reservada from lotes where numero_lote = 'OME-2502-A' and sucursal_id = 2", Integer.class))
			.isZero();
		org.assertj.core.api.Assertions.assertThat(
				jdbc.queryForObject("select count(*) from movimientos where tipo = 'SALIDA' and referencia like 'Pedido PED-1 pagado%'", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void lasReservasSinPagarExpiranYDevuelvenElStock() throws Exception {
		long id = crearReserva("2", "[{\"medicamentoId\":6,\"cantidad\":5}]");
		org.assertj.core.api.Assertions.assertThat(disponibleDelLote("VIC-2411-A", 2)).isZero();
		jdbc.update("update reservas set expira_en = ? where id = ?", java.sql.Timestamp.from(RelojDePrueba.AHORA.minusSeconds(60)), id);

		org.assertj.core.api.Assertions.assertThat(inventario.expirarVencidas()).isEqualTo(1);

		mvc.perform(get(BASE + "/reservas/" + id).with(CLIENTE)).andExpect(jsonPath("$.estado").value("EXPIRADA"));
		org.assertj.core.api.Assertions.assertThat(disponibleDelLote("VIC-2411-A", 2)).isEqualTo(5);
	}

	@Test
	void coberturaIndicaQueSucursalesPuedenEntregarElCarrito() throws Exception {
		String body = "{\"items\":[{\"medicamentoId\":1,\"cantidad\":25,\"nombre\":\"Paracetamol 500mg\"},{\"medicamentoId\":5,\"cantidad\":6}]}";
		mvc.perform(post(BASE + "/cobertura").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].completo").value(true))
			.andExpect(jsonPath("$[1].completo").value(false))
			.andExpect(jsonPath("$[1].faltantes", hasSize(2)))
			.andExpect(jsonPath("$[1].faltantes[0].disponible").value(20));
	}

	@Test
	void losLotesSoloLosVeElPersonalConSuEstadoDeVencimiento() throws Exception {
		mvc.perform(get(BASE + "/lotes")).andExpect(status().isUnauthorized());
		mvc.perform(get(BASE + "/lotes").with(CLIENTE)).andExpect(status().isForbidden());

		mvc.perform(get(BASE + "/lotes").param("estado", "VENCIDO").with(OPERADOR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].numeroLote", hasItem("AMX-2310-A")))
			.andExpect(jsonPath("$[*].numeroLote", hasItem("PAR-2312-Z")));
		mvc.perform(get(BASE + "/lotes").param("estado", "POR_VENCER").with(OPERADOR))
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].numeroLote").value("LOR-2409-A"))
			.andExpect(jsonPath("$[2].numeroLote").value("PIN-2512-A"))
			.andExpect(jsonPath("$[0].diasParaVencer").value(21));
	}

	@Test
	void ingresarUnLoteNuevo() throws Exception {
		String lote = "{\"medicamentoId\":3,\"sucursalId\":2,\"numeroLote\":\"amx-2609-b\",\"fechaVencimiento\":\"2028-03-31\",\"cantidad\":40}";
		mvc.perform(post(BASE + "/lotes").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(lote))
			.andExpect(status().isForbidden());
		mvc.perform(post(BASE + "/lotes").with(OPERADOR).contentType(MediaType.APPLICATION_JSON).content(lote))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.numeroLote").value("AMX-2609-B"))
			.andExpect(jsonPath("$.disponible").value(40))
			.andExpect(jsonPath("$.estado").value("VIGENTE"));
		// El mismo número de lote otra vez: 409.
		mvc.perform(post(BASE + "/lotes").with(OPERADOR).contentType(MediaType.APPLICATION_JSON).content(lote))
			.andExpect(status().isConflict());
		// Un lote ya vencido no se puede ingresar.
		mvc.perform(post(BASE + "/lotes").with(OPERADOR).contentType(MediaType.APPLICATION_JSON)
				.content(lote.replace("AMX-2609-B", "X-1").replace("amx-2609-b", "x-1").replace("2028-03-31", "2026-09-29")))
			.andExpect(status().isBadRequest());

		mvc.perform(get(BASE + "/disponibilidad/3")).andExpect(jsonPath("$[1].disponible").value(40));
	}

	@Test
	void darDeBajaUnLoteVencidoQuedaRegistrado() throws Exception {
		Long loteId = jdbc.queryForObject("select id from lotes where numero_lote = 'PAR-2312-Z'", Long.class);
		mvc.perform(post(BASE + "/lotes/" + loteId + "/merma").with(OPERADOR).contentType(MediaType.APPLICATION_JSON)
				.content("{\"cantidad\":6,\"motivo\":\"Vencido\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.disponible").value(0))
			.andExpect(jsonPath("$.estado").value("AGOTADO"));

		mvc.perform(get(BASE + "/lotes/" + loteId + "/movimientos").with(OPERADOR))
			.andExpect(jsonPath("$[0].tipo").value("MERMA"))
			.andExpect(jsonPath("$[0].cantidad").value(6));
	}

	@Test
	void resumenParaElPanel() throws Exception {
		mvc.perform(get(BASE + "/resumen").with(OPERADOR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unidadesVendibles").value(1059))
			.andExpect(jsonPath("$.lotesPorVencer").value(3))
			.andExpect(jsonPath("$.lotesVencidos").value(2))
			.andExpect(jsonPath("$.unidadesVencidas").value(16));
	}
}
