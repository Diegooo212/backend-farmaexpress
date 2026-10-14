package cl.farmaexpress.payments.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cl.farmaexpress.payments.proveedores.ProveedorNoDisponibleException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Reglas del circuit breaker (iguales para los tres proveedores, cada uno con su propio circuito):
 * de las últimas {@code ventana} llamadas, si fallan al menos {@code porcentaje-fallas}% (y hubo un
 * mínimo de llamadas), el circuito se abre durante {@code espera-abierto}. Solo cuentan como falla
 * los problemas del proveedor (sin respuesta, 5xx); un pago rechazado por el banco no es una falla.
 */
@Configuration
public class CircuitoPagosConfig {

	@Bean
	CircuitBreakerRegistry circuitBreakerRegistry(@Value("${farmaexpress.pagos.circuito.ventana}") int ventana,
			@Value("${farmaexpress.pagos.circuito.minimo-llamadas}") int minimo,
			@Value("${farmaexpress.pagos.circuito.porcentaje-fallas}") float porcentaje,
			@Value("${farmaexpress.pagos.circuito.espera-abierto}") Duration espera) {
		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
			.slidingWindowType(SlidingWindowType.COUNT_BASED)
			.slidingWindowSize(ventana)
			.minimumNumberOfCalls(minimo)
			.failureRateThreshold(porcentaje)
			.waitDurationInOpenState(espera)
			.permittedNumberOfCallsInHalfOpenState(1)
			.automaticTransitionFromOpenToHalfOpenEnabled(true)
			.recordExceptions(ProveedorNoDisponibleException.class)
			.build();
		return CircuitBreakerRegistry.of(config);
	}
}
