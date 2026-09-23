package cl.farmaexpress.bff.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración propia del BFF (prefijo "farmaexpress" en application.properties). */
@ConfigurationProperties(prefix = "farmaexpress")
public record FarmaexpressProperties(Cors cors, Services services) {

	public record Cors(List<String> allowedOrigins) {
	}

	public record Services(String catalogUrl, String prescriptionsUrl) {
	}
}
