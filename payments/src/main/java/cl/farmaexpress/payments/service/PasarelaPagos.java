package cl.farmaexpress.payments.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import cl.farmaexpress.payments.domain.MetodoPago;
import cl.farmaexpress.payments.domain.Pago;
import cl.farmaexpress.payments.proveedores.ProveedorNoDisponibleException;
import cl.farmaexpress.payments.proveedores.ProveedorPago;
import cl.farmaexpress.payments.proveedores.ProveedorPago.Redireccion;
import cl.farmaexpress.payments.proveedores.ProveedorPago.Resultado;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Punto único de llamada a las pasarelas. Cada proveedor tiene su propio circuit breaker
 * (Resilience4j), así una caída de Khipu no afecta a Webpay ni a Mercado Pago:
 * <ul>
 * <li><b>CERRADO</b>: las llamadas pasan normalmente y se cuentan las fallas;</li>
 * <li><b>ABIERTO</b>: con muchas fallas seguidas, se deja de llamar al proveedor por un tiempo y el
 * método aparece como "no disponible" en el checkout (el cliente elige otro, sin esperar);</li>
 * <li><b>SEMIABIERTO</b>: pasado ese tiempo se deja pasar una llamada de prueba; si funciona se cierra.</li>
 * </ul>
 */
@Component
public class PasarelaPagos {

	private static final Logger log = LoggerFactory.getLogger(PasarelaPagos.class);

	private final Map<MetodoPago, ProveedorPago> proveedores = new EnumMap<>(MetodoPago.class);
	private final Map<MetodoPago, CircuitBreaker> circuitos = new EnumMap<>(MetodoPago.class);
	private final SimulacionFallas fallas;

	public PasarelaPagos(List<ProveedorPago> lista, CircuitBreakerRegistry registro, SimulacionFallas fallas) {
		this.fallas = fallas;
		for (ProveedorPago proveedor : lista) {
			MetodoPago metodo = proveedor.metodo();
			proveedores.put(metodo, proveedor);
			CircuitBreaker circuito = registro.circuitBreaker(metodo.ruta());
			circuito.getEventPublisher().onStateTransition(e -> log.warn("Circuit breaker de {}: {}", metodo.nombre(),
					e.getStateTransition()));
			circuitos.put(metodo, circuito);
		}
	}

	public Pago.Modo modo(MetodoPago metodo) {
		return proveedor(metodo).modo();
	}

	/** @throws io.github.resilience4j.circuitbreaker.CallNotPermittedException si el circuito está abierto */
	public Redireccion iniciar(Pago pago, String descripcion, String urlRetorno) {
		return protegido(pago.getMetodo(), () -> proveedor(pago.getMetodo()).iniciar(pago, descripcion, urlRetorno));
	}

	public Resultado confirmar(Pago pago, Map<String, String> parametros) {
		return protegido(pago.getMetodo(), () -> proveedor(pago.getMetodo()).confirmar(pago, parametros));
	}

	public CircuitBreaker.State estado(MetodoPago metodo) {
		return circuitos.get(metodo).getState();
	}

	/** Un método está disponible salvo que su circuito esté abierto. */
	public boolean disponible(MetodoPago metodo) {
		CircuitBreaker.State estado = estado(metodo);
		return estado != CircuitBreaker.State.OPEN && estado != CircuitBreaker.State.FORCED_OPEN;
	}

	private <T> T protegido(MetodoPago metodo, Supplier<T> llamada) {
		return circuitos.get(metodo).executeSupplier(() -> {
			if (fallas.estaCaido(metodo)) {
				throw new ProveedorNoDisponibleException(metodo.nombre() + " no responde en este momento");
			}
			return llamada.get();
		});
	}

	private ProveedorPago proveedor(MetodoPago metodo) {
		ProveedorPago proveedor = proveedores.get(metodo);
		if (proveedor == null) {
			throw new IllegalStateException("No hay adaptador para " + metodo);
		}
		return proveedor;
	}
}
