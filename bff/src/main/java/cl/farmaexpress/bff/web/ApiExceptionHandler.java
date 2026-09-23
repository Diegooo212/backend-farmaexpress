package cl.farmaexpress.bff.web;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Errores en formato Problem Details: {"status": 409, "detail": "mensaje en español"}. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detalle = ex.getBindingResult().getAllErrors().stream()
			.map(error -> error.getDefaultMessage())
			.distinct()
			.collect(Collectors.joining(" "));
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
		problem.setTitle("Datos inválidos");
		return ResponseEntity.badRequest().body(problem);
	}

	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
			.body(ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo pesa más de 5 MB."));
	}

	/** Un microservicio respondió con error (p. ej. 409 sin stock): se devuelve su respuesta tal cual. */
	@ExceptionHandler(RestClientResponseException.class)
	public ResponseEntity<byte[]> handleRespuestaDeServicio(RestClientResponseException ex) {
		MediaType tipo = ex.getResponseHeaders() != null ? ex.getResponseHeaders().getContentType() : null;
		return ResponseEntity.status(ex.getStatusCode())
			.contentType(tipo != null ? tipo : MediaType.APPLICATION_PROBLEM_JSON)
			.body(ex.getResponseBodyAsByteArray());
	}

	/** Un microservicio no responde (apagado o caído). */
	@ExceptionHandler(ResourceAccessException.class)
	public ProblemDetail handleServicioNoDisponible(ResourceAccessException ex) {
		log.warn("Microservicio no disponible: {}", ex.getMessage());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"Un servicio de FarmaExpress no está disponible. Intenta de nuevo en unos minutos.");
		problem.setTitle("Servicio no disponible");
		return problem;
	}
}
