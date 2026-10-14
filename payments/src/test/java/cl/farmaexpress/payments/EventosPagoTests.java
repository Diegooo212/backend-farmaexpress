package cl.farmaexpress.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;

import cl.farmaexpress.payments.eventos.Eventos.PagoAprobado;
import cl.farmaexpress.payments.eventos.Eventos.PagoRechazado;
import cl.farmaexpress.payments.eventos.PublicadorEventos;

import cl.farmaexpress.payments.config.RabbitConfig;

/** El resultado de cada pago se publica en RabbitMQ (después del commit) para BFF, inventory y notifications. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@Import(EntraDePrueba.class)
@TestPropertySource(properties = { "farmaexpress.eventos.habilitado=true",
		"spring.datasource.url=jdbc:h2:mem:payments-eventos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" })
class EventosPagoTests {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	RabbitTemplate rabbit;

	private String[] pagar(long pedidoId, String resultado) throws Exception {
		String json = mvc.perform(post("/api/payments")
				.with(jwt().jwt(j -> j.claim("oid", "oid-cliente").claim("scp", "access_as_user"))
					.authorities(new SimpleGrantedAuthority("SCOPE_access_as_user")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"pedidoId\":%d,\"reservaId\":77,\"monto\":3290,\"metodo\":\"KHIPU\"}".formatted(pedidoId)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String pagoId = String.valueOf(((Number) JsonPath.read(json, "$.pagoId")).longValue());
		String token = UriComponentsBuilder.fromUriString(JsonPath.read(json, "$.redireccion.url")).build()
			.getQueryParams().getFirst("token");
		mvc.perform(get("/api/payments/retorno/khipu").param("pago", pagoId).param("token", token).param("resultado", resultado))
			.andExpect(status().isSeeOther());
		return new String[] { pagoId };
	}

	@Test
	void unPagoAprobadoPublicaPagoAprobado() throws Exception {
		String pagoId = pagar(500, "aprobado")[0];

		ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
		verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(PagoAprobado.ROUTING_KEY), evento.capture());
		PagoAprobado aprobado = (PagoAprobado) evento.getValue();
		assertThat(aprobado.pagoId()).isEqualTo(Long.valueOf(pagoId));
		assertThat(aprobado.pedidoId()).isEqualTo(500L);
		assertThat(aprobado.reservaId()).isEqualTo(77L);
		assertThat(aprobado.cuentaId()).isEqualTo("oid-cliente");
		assertThat(aprobado.monto()).isEqualTo(3290);
		assertThat(aprobado.metodo()).isEqualTo("KHIPU");
	}

	@Test
	void unPagoRechazadoPublicaPagoRechazadoConElMotivo() throws Exception {
		pagar(501, "rechazado");

		ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
		verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(PagoRechazado.ROUTING_KEY), evento.capture());
		assertThat(((PagoRechazado) evento.getValue()).motivo()).contains("rechazado");
	}
}
