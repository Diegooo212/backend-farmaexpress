package cl.farmaexpress.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import cl.farmaexpress.inventory.config.RabbitConfig;
import cl.farmaexpress.inventory.eventos.Eventos.StockCambiado;
import cl.farmaexpress.inventory.eventos.Eventos.StockProducto;
import cl.farmaexpress.inventory.service.InventarioService;

/**
 * El stock que se informa al catálogo es lo que se puede comprar en UN pedido: el de la sucursal con
 * más unidades vendibles, no la suma (un pedido sale de una sola farmacia o del almacén central).
 */
@SpringBootTest
@ActiveProfiles({ "h2", "test" })
@Import({ EntraDePrueba.class, RelojDePrueba.class })
@TestPropertySource(properties = { "farmaexpress.eventos.habilitado=true",
		"spring.datasource.url=jdbc:h2:mem:inventory-stock;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" })
class StockPublicadoTests {

	@Autowired
	InventarioService inventario;

	@MockitoBean
	RabbitTemplate rabbit;

	@Test
	void elCatalogoRecibeElMaximoPorPedidoYElTotalDeTodasLasSucursales() {
		inventario.publicarTodoElStock();

		ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
		verify(rabbit, atLeastOnce()).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(StockCambiado.ROUTING_KEY),
				evento.capture());
		Map<Long, Integer> stock = ((StockCambiado) evento.getValue()).items().stream()
			.collect(Collectors.toMap(StockProducto::medicamentoId, StockProducto::stock));
		Map<Long, Integer> total = ((StockCambiado) evento.getValue()).items().stream()
			.collect(Collectors.toMap(StockProducto::medicamentoId, StockProducto::stockTotal));

		// Paracetamol: central 80, Centro 20, Norte 10, Sur 10 (+6 vencidas que no cuentan) → 80, no 120.
		assertThat(stock).containsEntry(1L, 80);
		// Lo que ve el cliente: la suma de todas las sucursales.
		assertThat(total).containsEntry(1L, 120);
		// Ibuprofeno: central 5, Centro 3 → 5.
		assertThat(stock).containsEntry(2L, 5);
		// Amoxicilina: solo tiene un lote vencido → 0.
		assertThat(stock).containsEntry(3L, 0);
	}
}
