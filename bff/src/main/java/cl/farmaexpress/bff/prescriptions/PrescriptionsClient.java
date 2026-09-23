package cl.farmaexpress.bff.prescriptions;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import cl.farmaexpress.bff.web.Reenvio;

/** Cliente HTTP del microservicio de recetas. Siempre reenvía el JWT del usuario. */
@Component
public class PrescriptionsClient {

	private static final String RECETAS = "/api/prescriptions";

	private final RestClient client;

	public PrescriptionsClient(@Qualifier("prescriptionsRestClient") RestClient client) {
		this.client = client;
	}

	public ResponseEntity<byte[]> reenviar(HttpMethod method, String ruta, String jsonBody, String token) {
		return Reenvio.reenviar(client, method, RECETAS + ruta, jsonBody, MediaType.APPLICATION_JSON, token);
	}

	/** Reenvía el formulario multipart: parte "datos" (JSON) y parte "archivo" (foto o PDF). */
	public ResponseEntity<byte[]> crear(String datosJson, MultipartFile archivo, String token) throws IOException {
		MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();

		HttpHeaders datosHeaders = new HttpHeaders();
		datosHeaders.setContentType(MediaType.APPLICATION_JSON);
		partes.add("datos", new HttpEntity<>(datosJson, datosHeaders));

		if (archivo != null && !archivo.isEmpty()) {
			String nombre = archivo.getOriginalFilename();
			ByteArrayResource contenido = new ByteArrayResource(archivo.getBytes()) {
				@Override
				public String getFilename() {
					return nombre;
				}
			};
			HttpHeaders archivoHeaders = new HttpHeaders();
			archivoHeaders.setContentType(archivo.getContentType() != null
					? MediaType.parseMediaType(archivo.getContentType())
					: MediaType.APPLICATION_OCTET_STREAM);
			partes.add("archivo", new HttpEntity<>(contenido, archivoHeaders));
		}

		return Reenvio.reenviar(client, HttpMethod.POST, RECETAS, partes, MediaType.MULTIPART_FORM_DATA, token);
	}
}
