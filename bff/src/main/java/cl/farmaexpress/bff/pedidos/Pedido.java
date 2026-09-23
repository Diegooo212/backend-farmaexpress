package cl.farmaexpress.bff.pedidos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "pedidos")
public class Pedido {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usuario_id", nullable = false)
	private Long usuarioId;

	@Column(nullable = false)
	private Integer total;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;

	@OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<PedidoItem> items = new ArrayList<>();

	protected Pedido() {
	}

	public Pedido(Long usuarioId) {
		this.usuarioId = usuarioId;
		this.total = 0;
		this.creadoEn = Instant.now();
	}

	public void agregar(Long medicamentoId, String nombre, int precioUnitario, int cantidad) {
		items.add(new PedidoItem(this, medicamentoId, nombre, precioUnitario, cantidad));
		total += precioUnitario * cantidad;
	}

	public Long getId() {
		return id;
	}

	public Integer getTotal() {
		return total;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public List<PedidoItem> getItems() {
		return items;
	}
}
