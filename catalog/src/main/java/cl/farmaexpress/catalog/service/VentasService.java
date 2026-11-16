package cl.farmaexpress.catalog.service;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.farmaexpress.catalog.domain.MedicamentoRepository;
import cl.farmaexpress.catalog.domain.VentaProcesada;
import cl.farmaexpress.catalog.domain.VentaProcesadaRepository;

/** "Más vendidos": suma las unidades de cada venta confirmada. No conoce Kafka. */
@Service
public class VentasService {

	private static final Logger log = LoggerFactory.getLogger(VentasService.class);

	private final MedicamentoRepository medicamentos;
	private final VentaProcesadaRepository procesadas;

	public VentasService(MedicamentoRepository medicamentos, VentaProcesadaRepository procesadas) {
		this.medicamentos = medicamentos;
		this.procesadas = procesadas;
	}

	/**
	 * Suma las unidades vendidas de cada producto del pedido, una sola vez por pedido.
	 * @param unidades unidades por id de producto
	 * @return false si el pedido ya se había contado (evento repetido)
	 */
	@Transactional
	public boolean registrarVenta(Long pedidoId, Map<Long, Integer> unidades) {
		if (procesadas.existsById(pedidoId)) {
			log.info("Venta del pedido {} ya contada: se ignora el evento repetido", pedidoId);
			return false;
		}
		unidades.forEach((id, cantidad) -> {
			if (medicamentos.sumarVendidos(id, cantidad) == 0) {
				log.warn("Venta del pedido {}: el producto {} ya no está en el catálogo", pedidoId, id);
			}
		});
		procesadas.save(new VentaProcesada(pedidoId, Instant.now()));
		log.info("Venta del pedido {} sumada a más vendidos: {}", pedidoId, unidades);
		return true;
	}
}
