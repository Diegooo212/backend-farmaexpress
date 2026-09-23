package cl.farmaexpress.bff.carrito;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "carrito_items")
public class CarritoItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usuario_id", nullable = false)
	private Long usuarioId;

	@Column(name = "medicamento_id", nullable = false)
	private Long medicamentoId;

	@Column(nullable = false)
	private Integer cantidad;

	protected CarritoItem() {
	}

	public CarritoItem(Long usuarioId, Long medicamentoId, int cantidad) {
		this.usuarioId = usuarioId;
		this.medicamentoId = medicamentoId;
		this.cantidad = cantidad;
	}

	public Long getMedicamentoId() {
		return medicamentoId;
	}

	public Integer getCantidad() {
		return cantidad;
	}
}
