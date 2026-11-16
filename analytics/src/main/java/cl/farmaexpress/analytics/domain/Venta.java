package cl.farmaexpress.analytics.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** Una venta (pedido pagado) recibida desde Kafka. */
@Entity
@Table(name = "ventas")
public class Venta {

	@Id
	@Column(name = "pedido_id")
	private Long pedidoId;

	@Column(name = "evento_id", nullable = false, unique = true, length = 64)
	private String eventoId;

	@Column(nullable = false)
	private Instant fecha;

	@Column(name = "recibida_en", nullable = false)
	private Instant recibidaEn;

	@Column(nullable = false, length = 20)
	private String entrega;

	@Column(name = "sucursal_id")
	private Long sucursalId;

	@Column(name = "sucursal_nombre", length = 120)
	private String sucursalNombre;

	@Column(name = "metodo_pago", nullable = false, length = 30)
	private String metodoPago;

	@Column(name = "costo_despacho", nullable = false)
	private Integer costoDespacho;

	@Column(nullable = false)
	private Integer total;

	@Column(nullable = false)
	private Integer unidades;

	@Column(nullable = false)
	private Integer particion;

	@Column(name = "offset_kafka", nullable = false)
	private Long offsetKafka;

	@OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<VentaItem> items = new ArrayList<>();

	protected Venta() {
	}

	public Venta(Long pedidoId, String eventoId, Instant fecha, Instant recibidaEn, String entrega, Long sucursalId,
			String sucursalNombre, String metodoPago, int costoDespacho, int total, int particion, long offsetKafka) {
		this.pedidoId = pedidoId;
		this.eventoId = eventoId;
		this.fecha = fecha;
		this.recibidaEn = recibidaEn;
		this.entrega = entrega;
		this.sucursalId = sucursalId;
		this.sucursalNombre = sucursalNombre;
		this.metodoPago = metodoPago;
		this.costoDespacho = costoDespacho;
		this.total = total;
		this.unidades = 0;
		this.particion = particion;
		this.offsetKafka = offsetKafka;
	}

	public void agregar(Long medicamentoId, String nombre, int cantidad, int precioUnitario) {
		items.add(new VentaItem(this, medicamentoId, nombre, cantidad, precioUnitario));
		unidades += cantidad;
	}

	public Long getPedidoId() {
		return pedidoId;
	}

	public Instant getFecha() {
		return fecha;
	}

	public Instant getRecibidaEn() {
		return recibidaEn;
	}

	public String getEntrega() {
		return entrega;
	}

	public String getSucursalNombre() {
		return sucursalNombre;
	}

	public String getMetodoPago() {
		return metodoPago;
	}

	public Integer getTotal() {
		return total;
	}

	public Integer getUnidades() {
		return unidades;
	}

	public Integer getParticion() {
		return particion;
	}

	public Long getOffsetKafka() {
		return offsetKafka;
	}

	public List<VentaItem> getItems() {
		return items;
	}
}
