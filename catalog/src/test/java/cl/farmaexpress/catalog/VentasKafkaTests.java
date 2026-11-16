package cl.farmaexpress.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cl.farmaexpress.catalog.config.KafkaConfig;
import cl.farmaexpress.catalog.domain.MedicamentoRepository;
import cl.farmaexpress.catalog.eventos.EventoInvalidoException;
import cl.farmaexpress.catalog.eventos.VentasKafkaListener;
import cl.farmaexpress.catalog.eventos.VentasKafkaListener.VentaRegistrada;
import cl.farmaexpress.catalog.eventos.VentasKafkaListener.VentaRegistrada.Item;

/** Consumidor catalog-ventas: "más vendidos" a partir del tópico farmaexpress.ventas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "h2", "test" })
@Import(EntraDePrueba.class)
@Transactional
class VentasKafkaTests {

	@Autowired
	VentasKafkaListener listener;

	@Autowired
	MedicamentoRepository medicamentos;

	@Autowired
	MockMvc mvc;

	private static ConsumerRecord<String, VentaRegistrada> registro(VentaRegistrada venta) {
		return new ConsumerRecord<>(KafkaConfig.TOPICO_VENTAS, 0, 7L, venta == null ? null : String.valueOf(venta.pedidoId()),
				venta);
	}

	private int vendidos(Long id) {
		return medicamentos.findById(id).orElseThrow().getVendidos();
	}

	@Test
	void sumaLasUnidadesVendidasUnaSolaVezPorPedido() throws Exception {
		Long id = medicamentos.findAllByOrderByIdAsc().get(0).getId();
		int antes = vendidos(id);

		VentaRegistrada venta = new VentaRegistrada(9001L, List.of(new Item(id, 2), new Item(id, 1)));
		listener.alRegistrarseVenta(registro(venta));
		// El mismo evento otra vez (entrega "al menos una vez"): no suma de nuevo.
		listener.alRegistrarseVenta(registro(venta));

		assertThat(vendidos(id)).isEqualTo(antes + 3);
		mvc.perform(get("/api/catalog/medicamentos/" + id)).andExpect(jsonPath("$.vendidos").value(antes + 3));
	}

	@Test
	void unEventoInvalidoNoSeProcesaYVaAlDlt() {
		assertThatThrownBy(() -> listener.alRegistrarseVenta(registro(new VentaRegistrada(9002L, List.of()))))
			.isInstanceOf(EventoInvalidoException.class)
			.hasMessageContaining("items");
		assertThatThrownBy(() -> listener.alRegistrarseVenta(registro(new VentaRegistrada(9003L, List.of(new Item(1L, 0))))))
			.isInstanceOf(EventoInvalidoException.class)
			.hasMessageContaining("cantidad");
		assertThatThrownBy(() -> listener.alRegistrarseVenta(registro(null))).isInstanceOf(EventoInvalidoException.class);
	}
}
