package cl.farmaexpress.bff.pedidos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pedido_items")
public class PedidoItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_id", nullable = false)
	private Pedido pedido;

	@Column(name = "medicamento_id", nullable = false)
	private Long medicamentoId;

	@Column(nullable = false, length = 150)
	private String nombre;

	@Column(name = "precio_unitario", nullable = false)
	private Integer precioUnitario;

	@Column(nullable = false)
	private Integer cantidad;

	protected PedidoItem() {
	}

	PedidoItem(Pedido pedido, Long medicamentoId, String nombre, int precioUnitario, int cantidad) {
		this.pedido = pedido;
		this.medicamentoId = medicamentoId;
		this.nombre = nombre;
		this.precioUnitario = precioUnitario;
		this.cantidad = cantidad;
	}

	public Long getMedicamentoId() {
		return medicamentoId;
	}

	public String getNombre() {
		return nombre;
	}

	public Integer getPrecioUnitario() {
		return precioUnitario;
	}

	public Integer getCantidad() {
		return cantidad;
	}
}
