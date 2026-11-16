package cl.farmaexpress.catalog.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Pedido cuya venta ya se sumó a "vendidos": hace idempotente al consumidor de Kafka. */
@Entity
@Table(name = "ventas_procesadas")
public class VentaProcesada {

	@Id
	@Column(name = "pedido_id")
	private Long pedidoId;

	@Column(name = "procesada_en", nullable = false)
	private Instant procesadaEn;

	protected VentaProcesada() {
	}

	public VentaProcesada(Long pedidoId, Instant procesadaEn) {
		this.pedidoId = pedidoId;
		this.procesadaEn = procesadaEn;
	}

	public Long getPedidoId() {
		return pedidoId;
	}
}
