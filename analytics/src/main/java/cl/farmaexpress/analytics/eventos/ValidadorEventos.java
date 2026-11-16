package cl.farmaexpress.analytics.eventos;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.stereotype.Component;

/** Valida cada evento contra su contrato (Bean Validation) antes de procesarlo. */
@Component
public class ValidadorEventos {

	private final Validator validator;

	public ValidadorEventos(Validator validator) {
		this.validator = validator;
	}

	public <T> T validar(T evento) {
		if (evento == null) {
			throw new EventoInvalidoException("El mensaje no trae contenido.");
		}
		Set<ConstraintViolation<T>> errores = validator.validate(evento);
		if (!errores.isEmpty()) {
			throw new EventoInvalidoException(errores.stream()
				.sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
				.map(v -> v.getPropertyPath() + " " + v.getMessage())
				.collect(Collectors.joining("; ")));
		}
		return evento;
	}

	/** Además del formato, el total debe cuadrar: productos + despacho. */
	public VentaRegistrada validarVenta(VentaRegistrada venta) {
		validar(venta);
		int productos = venta.items().stream().mapToInt(VentaRegistrada.Item::subtotal).sum();
		if (productos + venta.costoDespacho() != venta.total()) {
			throw new EventoInvalidoException("El total del pedido " + venta.pedidoId() + " (" + venta.total()
					+ ") no cuadra con sus productos (" + productos + ") más el despacho (" + venta.costoDespacho() + ").");
		}
		return venta;
	}
}
