package cl.farmaexpress.kafkaadmin.web;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import cl.farmaexpress.kafkaadmin.service.KafkaAdminException;

/** Todas las respuestas de error en formato Problem Details (RFC 9457) con mensajes en español. */
@RestControllerAdvice
public class ManejoErrores {

	@ExceptionHandler(KafkaAdminException.class)
	ProblemDetail negocio(KafkaAdminException e) {
		return ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
	}

	/** Cuerpo con campos inválidos: se devuelve el detalle de cada campo. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail cuerpoInvalido(MethodArgumentNotValidException e) {
		Map<String, String> campos = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors().forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				campos.isEmpty() ? "Datos inválidos." : String.join(" ", campos.values()));
		problema.setProperty("campos", campos);
		return problema;
	}

	/** Parámetros de ruta o de consulta inválidos. */
	@ExceptionHandler({ HandlerMethodValidationException.class, ConstraintViolationException.class })
	ProblemDetail parametroInvalido(Exception e) {
		String detalle = e instanceof HandlerMethodValidationException h
				? h.getAllErrors().stream().map(err -> err.getDefaultMessage()).findFirst().orElse("Parámetro inválido.")
				: ((ConstraintViolationException) e).getConstraintViolations().iterator().next().getMessage();
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
	}

	/** JSON mal formado o un valor no permitido (por ejemplo "posicion": "MEDIO"). */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail jsonInvalido(HttpMessageNotReadableException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"El cuerpo no es un JSON válido o tiene un valor no permitido (posición: EARLIEST o LATEST).");
	}
}
