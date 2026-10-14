package cl.farmaexpress.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tareas automáticas del inventario. */
@Component
public class TareasInventario {

	private static final Logger log = LoggerFactory.getLogger(TareasInventario.class);

	private final InventarioService inventario;

	public TareasInventario(InventarioService inventario) {
		this.inventario = inventario;
	}

	/** Cada minuto libera las reservas que no se pagaron a tiempo. */
	@Scheduled(fixedDelayString = "${farmaexpress.inventario.revisar-reservas-ms:60000}", initialDelay = 30000)
	public void expirarReservas() {
		int expiradas = inventario.expirarVencidas();
		if (expiradas > 0) {
			log.info("Se liberaron {} reservas sin pagar", expiradas);
		}
	}

	/** Al iniciar, el catálogo recibe el stock vendible actual (por si hubo cambios mientras estaba apagado). */
	@EventListener(ApplicationReadyEvent.class)
	public void sincronizarCatalogo() {
		inventario.publicarTodoElStock();
	}
}
