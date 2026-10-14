package cl.farmaexpress.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Cuántas unidades de un lote específico quedaron apartadas para la reserva. */
@Entity
@Table(name = "reserva_items")
public class ReservaItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reserva_id")
	private Reserva reserva;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lote_id")
	private Lote lote;

	@Column(name = "medicamento_id", nullable = false)
	private Long medicamentoId;

	@Column(nullable = false)
	private int cantidad;

	protected ReservaItem() {
	}

	ReservaItem(Reserva reserva, Lote lote, int cantidad) {
		this.reserva = reserva;
		this.lote = lote;
		this.medicamentoId = lote.getMedicamentoId();
		this.cantidad = cantidad;
	}

	public Lote getLote() {
		return lote;
	}

	public Long getMedicamentoId() {
		return medicamentoId;
	}

	public int getCantidad() {
		return cantidad;
	}
}
