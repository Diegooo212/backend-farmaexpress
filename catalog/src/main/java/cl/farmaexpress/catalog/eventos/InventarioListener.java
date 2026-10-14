package cl.farmaexpress.catalog.eventos;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.farmaexpress.catalog.config.RabbitConfig;
import cl.farmaexpress.catalog.service.MedicamentoService;

/**
 * Consumidor del dominio "stock". El stock que muestra la tienda lo decide el microservicio de
 * inventario (lotes no vencidos de todas las sucursales): cada vez que cambia llega el evento
 * inventario.stock-cambiado. El mensaje se confirma de forma explícita (ACK manual).
 */
@Component
public class InventarioListener {

	/** Evento publicado por inventory. */
	public record StockCambiado(String eventoId, List<StockProducto> items, Instant fecha) {
	}

	/** {@code stock}: máximo por pedido; {@code stockTotal}: todas las sucursales. */
	public record StockProducto(Long medicamentoId, int stock, int stockTotal) {
	}

	private final MedicamentoService medicamentos;
	private final ConfirmacionMensajes confirmacion;

	public InventarioListener(MedicamentoService medicamentos, ConfirmacionMensajes confirmacion) {
		this.medicamentos = medicamentos;
		this.confirmacion = confirmacion;
	}

	@RabbitListener(queues = RabbitConfig.COLA_STOCK)
	public void alCambiarStock(StockCambiado evento, Message mensaje, Channel canal) {
		confirmacion.procesar(mensaje, canal, () -> stockCambiado(evento));
	}

	public void stockCambiado(StockCambiado evento) {
		if (evento.items() == null || evento.items().isEmpty()) {
			return;
		}
		Map<Long, int[]> stock = evento.items().stream()
			.collect(Collectors.toMap(StockProducto::medicamentoId, s -> new int[] { s.stock(), s.stockTotal() }, (a, b) -> b));
		medicamentos.sincronizarStock(stock);
	}
}
