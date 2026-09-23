package cl.farmaexpress.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FarmaexpressBffApplication {

	public static void main(String[] args) {
		SpringApplication.run(FarmaexpressBffApplication.class, args);
	}

}
