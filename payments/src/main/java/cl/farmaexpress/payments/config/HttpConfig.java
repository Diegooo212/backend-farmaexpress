package cl.farmaexpress.payments.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP para las pasarelas con tiempos de espera cortos: si un proveedor se cuelga, la
 * llamada falla rápido, cuenta como falla en el circuit breaker y el cliente puede elegir otro medio.
 */
@Configuration
public class HttpConfig {

	@Bean("pasarelas")
	RestClient.Builder pasarelas(@Value("${farmaexpress.pagos.timeout-conexion:3s}") Duration conexion,
			@Value("${farmaexpress.pagos.timeout-respuesta:8s}") Duration respuesta) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(conexion).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(respuesta);
		return RestClient.builder().requestFactory(factory);
	}
}
