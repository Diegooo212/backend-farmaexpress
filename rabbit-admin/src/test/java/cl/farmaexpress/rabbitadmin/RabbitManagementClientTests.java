package cl.farmaexpress.rabbitadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import cl.farmaexpress.rabbitadmin.config.RabbitAdminProperties;
import cl.farmaexpress.rabbitadmin.service.RabbitAdminException;
import cl.farmaexpress.rabbitadmin.service.RabbitManagementClient;

/** URLs exactas que se envían a la API de administración de RabbitMQ (el vhost "/" va como %2F, una sola vez). */
class RabbitManagementClientTests {

	private final RestClient.Builder builder = RestClient.builder().baseUrl("http://rabbit:15672");
	private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
	private final RabbitManagementClient client = new RabbitManagementClient(builder.build(),
			new RabbitAdminProperties("http://rabbit:15672", "/", "farmaexpress.eventos.dlx", List.of("amq.")));

	@Test
	void codificaElVhostUnaSolaVez() {
		servidor.expect(requestTo("http://rabbit:15672/api/queues/%2F"))
			.andRespond(withSuccess("[{\"name\":\"tickets\"}]", MediaType.APPLICATION_JSON));
		servidor.expect(requestTo("http://rabbit:15672/api/exchanges/%2F/tickets.eventos"))
			.andRespond(withResourceNotFound());

		assertThat(client.colas()).hasSize(1);
		assertThat(client.exchange("tickets.eventos")).isEmpty();
		servidor.verify();
	}

	@Test
	void unaRespuestaInesperadaSeInformaComo502() {
		servidor.expect(requestTo("http://rabbit:15672/api/bindings/%2F")).andRespond(withResourceNotFound());
		assertThatThrownBy(client::bindings).isInstanceOf(RabbitAdminException.class).hasMessageContaining("404");
	}
}
