package cl.farmaexpress.analytics.domain;

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
@Table(name = "venta_items")
public class VentaItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_id", nullable = false)
	private Venta venta;

	@Column(name = "medicamento_id", nullable = false)
	private Long medicamentoId;

	@Column(nullable = false, length = 150)
	private String nombre;

	@Column(nullable = false)
	private Integer cantidad;

	@Column(name = "precio_unitario", nullable = false)
	private Integer precioUnitario;

	@Column(nullable = false)
	private Integer subtotal;

	protected VentaItem() {
	}

	VentaItem(Venta venta, Long medicamentoId, String nombre, int cantidad, int precioUnitario) {
		this.venta = venta;
		this.medicamentoId = medicamentoId;
		this.nombre = nombre;
		this.cantidad = cantidad;
		this.precioUnitario = precioUnitario;
		this.subtotal = cantidad * precioUnitario;
	}

	public Long getMedicamentoId() {
		return medicamentoId;
	}

	public String getNombre() {
		return nombre;
	}

	public Integer getCantidad() {
		return cantidad;
	}

	public Integer getSubtotal() {
		return subtotal;
	}
}
