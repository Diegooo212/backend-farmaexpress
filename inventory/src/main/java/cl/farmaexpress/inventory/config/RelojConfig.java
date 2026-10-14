package cl.farmaexpress.inventory.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Reloj del inventario en la zona horaria de las farmacias: un lote "vence hoy" según la fecha de
 * Chile, no la del servidor. Las pruebas lo reemplazan por un reloj fijo.
 */
@Configuration
@EnableScheduling
public class RelojConfig {

	@Bean
	Clock reloj(@Value("${farmaexpress.zona-horaria}") String zona) {
		return Clock.system(ZoneId.of(zona));
	}
}
