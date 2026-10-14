package cl.farmaexpress.notifications;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import cl.farmaexpress.notifications.eventos.EventosListener;
import cl.farmaexpress.notifications.eventos.PagoAprobado;
import cl.farmaexpress.notifications.eventos.PagoRechazado;
import cl.farmaexpress.notifications.eventos.PedidoEstadoCambiado;
import cl.farmaexpress.notifications.eventos.RecetaEstadoCambiado;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@Import(EntraDePrueba.class)
@Transactional
class NotificacionesApiTests {

	private static final String BASE = "/api/notifications";
	private static final Instant AHORA = Instant.parse("2026-09-29T15:00:00Z");

	@Autowired
	MockMvc mvc;

	/** El mismo bean que recibe los mensajes de RabbitMQ. */
	@Autowired
	EventosListener listener;

	private static RequestPostProcessor cuenta(String oid) {
		return jwt().jwt(j -> j.subject("sub-" + oid).claim("oid", oid).claim("scp", "access_as_user"))
			.authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"));
	}

	private static RecetaEstadoCambiado receta(String eventoId, String cuentaId, String estado, String nota) {
		return new RecetaEstadoCambiado(eventoId, 7L, cuentaId, estado, nota, "RETIRO_TIENDA", "Farmacia Centro", AHORA);
	}

	@Test
	void sinTokenResponde401() throws Exception {
		mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
	}

	@Test
	void conTokenFirmadoPeroDeOtraApiResponde401() throws Exception {
		String token = EntraDePrueba.token(Map.of("aud", "otra-api"));
		mvc.perform(get(BASE).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
	}

	@Test
	void unCambioDeRecetaLlegaSoloASuDueno() throws Exception {
		listener.recetaEstadoCambiado(receta("ev-1", "oid-ana", "RECHAZADA", "La receta está vencida"));

		mvc.perform(get(BASE).with(cuenta("oid-ana")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].tipo").value("RECETA"))
			.andExpect(jsonPath("$[0].titulo").value("Receta #7"))
			.andExpect(jsonPath("$[0].estado").value("RECHAZADA"))
			.andExpect(jsonPath("$[0].mensaje").value("Tu receta fue rechazada."))
			.andExpect(jsonPath("$[0].detalle").value("La receta está vencida"))
			.andExpect(jsonPath("$[0].leida").value(false));

		mvc.perform(get(BASE).with(cuenta("oid-otra-persona")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void recetaListaParaRetiroIndicaLaFarmacia() throws Exception {
		listener.recetaEstadoCambiado(receta("ev-retiro", "oid-ana", "LISTA_RETIRO", null));

		mvc.perform(get(BASE).with(cuenta("oid-ana")))
			.andExpect(jsonPath("$[0].mensaje").value("Tu receta está lista para retiro en Farmacia Centro."));
	}

	@Test
	void unEventoRepetidoNoDuplicaElAviso() throws Exception {
		RecetaEstadoCambiado evento = receta("ev-repetido", "oid-ana", "VALIDADA", null);
		listener.recetaEstadoCambiado(evento);
		listener.recetaEstadoCambiado(evento);

		mvc.perform(get(BASE).with(cuenta("oid-ana"))).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void unPagoAprobadoAvisaConElMontoYElMedio() throws Exception {
		listener.pagoAprobado(new PagoAprobado("ev-pago", 9L, 5L, 3L, "oid-ana", 12990, "WEBPAY", "1213", AHORA));

		mvc.perform(get(BASE).with(cuenta("oid-ana")))
			.andExpect(jsonPath("$[0].tipo").value("PEDIDO"))
			.andExpect(jsonPath("$[0].titulo").value("Pedido #5"))
			.andExpect(jsonPath("$[0].estado").value("PAGO_APROBADO"))
			.andExpect(jsonPath("$[0].mensaje").value("Pago aprobado por $12.990 con Webpay. Estamos preparando tu pedido."));
	}

	@Test
	void unPagoRechazadoAvisaElMotivo() throws Exception {
		listener.pagoRechazado(new PagoRechazado("ev-rech", 9L, 6L, 3L, "oid-ana", 5000, "KHIPU", "Cancelaste la transferencia en Khipu.", AHORA));

		mvc.perform(get(BASE).with(cuenta("oid-ana")))
			.andExpect(jsonPath("$[0].estado").value("PAGO_RECHAZADO"))
			.andExpect(jsonPath("$[0].mensaje").value("Tu pago con Khipu no se completó. Puedes intentarlo con otro medio de pago."))
			.andExpect(jsonPath("$[0].detalle").value("Cancelaste la transferencia en Khipu."));
	}

	@Test
	void unPedidoListoParaRetiroIndicaLaFarmacia() throws Exception {
		listener.pedidoEstadoCambiado(new PedidoEstadoCambiado("ev-listo", 12L, "oid-ana", "LISTO_RETIRO", "RETIRO",
				"Farmacia Centro", null, AHORA));

		mvc.perform(get(BASE).with(cuenta("oid-ana")))
			.andExpect(jsonPath("$[0].titulo").value("Pedido #12"))
			.andExpect(jsonPath("$[0].estado").value("PEDIDO_LISTO_RETIRO"))
			.andExpect(jsonPath("$[0].mensaje").value(
					"Tu pedido está listo para retiro en Farmacia Centro. Lleva tu carnet de identidad."));
	}

	@Test
	void marcarLeidasSoloAfectaALaPropiaCuenta() throws Exception {
		listener.recetaEstadoCambiado(receta("ev-a", "oid-ana", "VALIDADA", null));
		listener.recetaEstadoCambiado(receta("ev-b", "oid-beto", "VALIDADA", null));

		mvc.perform(put(BASE + "/leidas").with(cuenta("oid-ana"))).andExpect(status().isNoContent());

		mvc.perform(get(BASE).with(cuenta("oid-ana"))).andExpect(jsonPath("$[0].leida").value(true));
		mvc.perform(get(BASE).with(cuenta("oid-beto"))).andExpect(jsonPath("$[0].leida").value(false));
	}

	@Test
	void unEventoSinDestinatarioSeRechazaParaQueVayaALaColaDeFallidos() {
		assertThatThrownBy(() -> listener.recetaEstadoCambiado(receta("ev-sin-cuenta", null, "VALIDADA", null)))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
