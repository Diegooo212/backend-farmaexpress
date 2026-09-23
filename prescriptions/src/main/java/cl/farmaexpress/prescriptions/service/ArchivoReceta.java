package cl.farmaexpress.prescriptions.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Revisa el archivo adjunto. No basta con el tipo que declara el navegador: también se revisan
 * los primeros bytes, para que nadie suba, por ejemplo, un HTML disfrazado de imagen.
 */
public record ArchivoReceta(String nombre, String tipo, byte[] contenido) {

	public static final long TAMANO_MAXIMO = 5L * 1024 * 1024;

	private static final Map<String, String> TIPOS = Map.of(
			"image/jpeg", "JPG",
			"image/png", "PNG",
			"image/webp", "WEBP",
			"application/pdf", "PDF");

	public static ArchivoReceta validar(MultipartFile archivo) {
		if (archivo == null || archivo.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adjunta la receta médica para poder validarla.");
		}
		if (archivo.getSize() > TAMANO_MAXIMO) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo pesa más de 5 MB.");
		}

		byte[] contenido;
		try {
			contenido = archivo.getBytes();
		}
		catch (IOException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo adjunto.");
		}

		String tipo = detectarTipo(contenido);
		if (tipo == null) {
			throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
					"Solo se aceptan fotos (JPG, PNG, WEBP) o PDF.");
		}

		String nombre = archivo.getOriginalFilename();
		if (nombre == null || nombre.isBlank()) {
			nombre = "receta." + TIPOS.get(tipo).toLowerCase();
		}
		// Solo el nombre, sin rutas, y con largo acotado.
		nombre = nombre.replaceAll("^.*[\\\\/]", "");
		if (nombre.length() > 255) {
			nombre = nombre.substring(nombre.length() - 255);
		}
		return new ArchivoReceta(nombre, tipo, contenido);
	}

	static String detectarTipo(byte[] b) {
		if (empiezaCon(b, 0xFF, 0xD8, 0xFF)) {
			return "image/jpeg";
		}
		if (empiezaCon(b, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
			return "image/png";
		}
		if (empiezaCon(b, '%', 'P', 'D', 'F', '-')) {
			return "application/pdf";
		}
		if (b.length >= 12 && empiezaCon(b, 'R', 'I', 'F', 'F')
				&& Arrays.equals(Arrays.copyOfRange(b, 8, 12), new byte[] { 'W', 'E', 'B', 'P' })) {
			return "image/webp";
		}
		return null;
	}

	private static boolean empiezaCon(byte[] datos, int... firma) {
		if (datos.length < firma.length) {
			return false;
		}
		for (int i = 0; i < firma.length; i++) {
			if ((datos[i] & 0xFF) != firma[i]) {
				return false;
			}
		}
		return true;
	}
}
