package cl.farmaexpress.inventory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * "Hoy" es el 29 de septiembre de 2026 en todas las pruebas, así los lotes de la carga inicial
 * siempre están en el mismo estado (vigentes, por vencer o vencidos).
 */
@TestConfiguration
public class RelojDePrueba {

	public static final Instant AHORA = Instant.parse("2026-09-29T15:00:00Z");

	@Bean
	@Primary
	Clock relojFijo() {
		return Clock.fixed(AHORA, ZoneId.of("America/Santiago"));
	}
}
