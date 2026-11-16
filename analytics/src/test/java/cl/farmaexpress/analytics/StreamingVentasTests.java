package cl.farmaexpress.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import cl.farmaexpress.analytics.domain.StockProductoRepository;
import cl.farmaexpress.analytics.domain.VentaRepository;

/**
 * Flujo de streaming completo contra un broker Kafka real (embebido): se publican eventos como lo hacen
 * el BFF e inventory, y se verifica lo que la analítica consume, ignora o desvía al dead letter topic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@Import(EntraDePrueba.class)
@EmbeddedKafka(count = 1, partitions = 3,
		topics = { "farmaexpress.ventas", "farmaexpress.stock", "farmaexpress.ventas.dlt", "farmaexpress.stock.dlt" })
class StreamingVentasTests {

	@Autowired
	EmbeddedKafkaBroker kafka;

	@Autowired
	VentaRepository ventas;

	@Autowired
	StockProductoRepository stock;

	@Autowired
	MockMvc mvc;

	private KafkaProducer<String, String> productor;

	private static RequestPostProcessor con(String... permisos) {
		return jwt().authorities(java.util.Arrays.stream(permisos).map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new));
	}

	@BeforeEach
	void productor() {
		productor = new KafkaProducer<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString(),
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
	}

	@AfterEach
	void cerrar() {
		productor.close();
	}

	private void publicar(String topico, String clave, String valor) throws Exception {
		productor.send(new ProducerRecord<>(topico, clave, valor)).get();
	}

	private static String venta(long pedido, int total, int despacho, String items) {
		return """
				{"eventoId":"%s","version":1,"pedidoId":%d,"fecha":"%s","entrega":"RETIRO","sucursalId":1,
				 "sucursalNombre":"FarmaExpress Providencia","metodoPago":"WEBPAY","costoDespacho":%d,"total":%d,
				 "items":[%s]}""".formatted(UUID.randomUUID(), pedido, Instant.now(), despacho, total, items);
	}

	private static void esperar(BooleanSupplier condicion, String descripcion) throws InterruptedException {
		long limite = System.currentTimeMillis() + 30_000;
		while (!condicion.getAsBoolean()) {
			if (System.currentTimeMillis() > limite) {
				throw new AssertionError("No se cumplió a tiempo: " + descripcion);
			}
			Thread.sleep(200);
		}
	}

	/** Lee el DLT desde el inicio hasta encontrar el mensaje con la clave indicada. */
	private ConsumerRecord<String, byte[]> leerDlt(String topico, String clave) {
		try (KafkaConsumer<String, byte[]> consumidor = new KafkaConsumer<>(Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString(),
				ConsumerConfig.GROUP_ID_CONFIG, "prueba-dlt-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class))) {
			consumidor.subscribe(List.of(topico));
			long limite = System.currentTimeMillis() + 30_000;
			while (System.currentTimeMillis() < limite) {
				for (ConsumerRecord<String, byte[]> r : consumidor.poll(Duration.ofMillis(500))) {
					if (clave.equals(r.key())) {
						return r;
					}
				}
			}
		}
		throw new AssertionError("No llegó al DLT el mensaje con clave " + clave);
	}

	private static String header(ConsumerRecord<?, ?> r, String nombre) {
		Header h = r.headers().lastHeader(nombre);
		return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
	}

	@Test
	void registraLaVentaConSuParticionYOffsetUnaSolaVez() throws Exception {
		String evento = venta(1001, 7_980, 0, """
				{"medicamentoId":1,"nombre":"Paracetamol 500 mg","cantidad":2,"precioUnitario":1990},
				{"medicamentoId":2,"nombre":"Ibuprofeno 400 mg","cantidad":2,"precioUnitario":2000}""");
		publicar("farmaexpress.ventas", "1001", evento);
		esperar(() -> ventas.existsById(1001L), "la venta 1001 se registra");

		// El mismo pedido llega otra vez (Kafka entrega "al menos una vez"): no se duplica.
		publicar("farmaexpress.ventas", "1001", venta(1001, 7_980, 0,
				"{\"medicamentoId\":1,\"nombre\":\"Paracetamol 500 mg\",\"cantidad\":4,\"precioUnitario\":1995}"));
		publicar("farmaexpress.ventas", "1002", venta(1002, 3_990, 2_000,
				"{\"medicamentoId\":1,\"nombre\":\"Paracetamol 500 mg\",\"cantidad\":1,\"precioUnitario\":1990}"));
		esperar(() -> ventas.existsById(1002L), "la venta 1002 se registra");

		assertThat(ventas.findById(1001L)).get().satisfies(v -> {
			assertThat(v.getTotal()).isEqualTo(7_980);
			assertThat(v.getUnidades()).isEqualTo(4);
			assertThat(v.getParticion()).isBetween(0, 2);
		});

		mvc.perform(get("/api/analytics/tablero")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/analytics/tablero").with(con("SCOPE_access_as_user", "ROLE_Cliente"))).andExpect(status().isForbidden());
		mvc.perform(get("/api/analytics/tablero").with(con("SCOPE_access_as_user", "ROLE_Operador")))
			.andExpect(status().isOk())
			// Las otras pruebas también registran ventas: se revisan los pedidos de esta prueba.
			.andExpect(jsonPath("$.recientes[?(@.pedidoId==1001)].total", hasItem(7_980)))
			.andExpect(jsonPath("$.recientes[?(@.pedidoId==1002)].total", hasItem(3_990)))
			.andExpect(jsonPath("$.masVendidos[?(@.nombre=='Paracetamol 500 mg')].unidades", hasItem(3)))
			.andExpect(jsonPath("$.recientes[0].particion").isNumber())
			.andExpect(jsonPath("$.ventasPorHora.length()").value(12));
	}

	@Test
	void unJsonIlegibleVaAlDltSinDetenerAlConsumidor() throws Exception {
		publicar("farmaexpress.ventas", "2001", "{esto no es json");
		publicar("farmaexpress.ventas", "2002", venta(2002, 1_990, 0,
				"{\"medicamentoId\":3,\"nombre\":\"Loratadina 10 mg\",\"cantidad\":1,\"precioUnitario\":1990}"));

		ConsumerRecord<String, byte[]> fallido = leerDlt("farmaexpress.ventas.dlt", "2001");
		assertThat(new String(fallido.value(), StandardCharsets.UTF_8)).isEqualTo("{esto no es json");
		assertThat(header(fallido, "kafka_dlt-original-topic")).isEqualTo("farmaexpress.ventas");
		// El mensaje siguiente de la misma partición se procesó igual.
		esperar(() -> ventas.existsById(2002L), "la venta 2002 se registra después del mensaje defectuoso");
	}

	@Test
	void unEventoConDatosInvalidosVaAlDltConElMotivo() throws Exception {
		// El total no cuadra: 1 x 1990 + despacho 0 != 5000.
		publicar("farmaexpress.ventas", "3001", venta(3001, 5_000, 0,
				"{\"medicamentoId\":3,\"nombre\":\"Loratadina 10 mg\",\"cantidad\":1,\"precioUnitario\":1990}"));
		ConsumerRecord<String, byte[]> fallido = leerDlt("farmaexpress.ventas.dlt", "3001");
		assertThat(header(fallido, "kafka_dlt-exception-message")).contains("no cuadra");
		assertThat(ventas.existsById(3001L)).isFalse();

		// Sin productos: falla la validación del contrato.
		publicar("farmaexpress.ventas", "3002", venta(3002, 1_000, 0, ""));
		assertThat(header(leerDlt("farmaexpress.ventas.dlt", "3002"), "kafka_dlt-exception-message")).contains("items");
	}

	@Test
	void mantieneElStockVigenteYAplicaLasLapidas() throws Exception {
		String evento = """
				{"eventoId":"%s","version":1,"medicamentoId":77,"stockPorPedido":3,"stockTotal":5,"fecha":"%s"}"""
			.formatted(UUID.randomUUID(), Instant.now());
		publicar("farmaexpress.stock", "77", evento);
		esperar(() -> stock.existsById(77L), "el stock del producto 77 se registra");

		mvc.perform(get("/api/analytics/tablero").with(con("SCOPE_access_as_user", "ROLE_Admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stockBajo[?(@.medicamentoId==77)].stockTotal").value(5));

		// Lápida: clave sin contenido. En un tópico compactado indica que el producto dejó de existir.
		productor.send(new ProducerRecord<>("farmaexpress.stock", "77", null)).get();
		esperar(() -> !stock.existsById(77L), "la lápida elimina el stock del producto 77");

		List<String> claves = new ArrayList<>();
		publicar("farmaexpress.stock", "88", evento);
		claves.add(header(leerDlt("farmaexpress.stock.dlt", "88"), "kafka_dlt-exception-message"));
		assertThat(claves.get(0)).contains("no es el producto");
	}
}
