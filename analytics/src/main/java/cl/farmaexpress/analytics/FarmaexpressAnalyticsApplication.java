package cl.farmaexpress.analytics;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FarmaexpressAnalyticsApplication {

	public static void main(String[] args) {
		SpringApplication.run(FarmaexpressAnalyticsApplication.class, args);
	}

	@Bean
	Clock reloj() {
		return Clock.systemUTC();
	}
}
