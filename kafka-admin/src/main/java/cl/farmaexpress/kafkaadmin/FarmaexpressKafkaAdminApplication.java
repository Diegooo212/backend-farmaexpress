package cl.farmaexpress.kafkaadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FarmaexpressKafkaAdminApplication {

	public static void main(String[] args) {
		SpringApplication.run(FarmaexpressKafkaAdminApplication.class, args);
	}
}
