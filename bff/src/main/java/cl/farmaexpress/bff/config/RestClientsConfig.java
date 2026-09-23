package cl.farmaexpress.bff.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Un RestClient por microservicio, con tiempos de espera acotados para no colgar al frontend. */
@Configuration
public class RestClientsConfig {

	private static final Duration CONEXION = Duration.ofSeconds(3);
	private static final Duration RESPUESTA = Duration.ofSeconds(20);

	@Bean
	RestClient catalogRestClient(RestClient.Builder builder, FarmaexpressProperties props) {
		return builder.clone().baseUrl(props.services().catalogUrl()).requestFactory(requestFactory()).build();
	}

	@Bean
	RestClient prescriptionsRestClient(RestClient.Builder builder, FarmaexpressProperties props) {
		return builder.clone().baseUrl(props.services().prescriptionsUrl()).requestFactory(requestFactory()).build();
	}

	private static JdkClientHttpRequestFactory requestFactory() {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONEXION).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(RESPUESTA);
		return factory;
	}
}
