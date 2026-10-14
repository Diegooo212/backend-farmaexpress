package cl.farmaexpress.bff.pedidos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pedidos")
public class Pedido {

	public enum Estado {
		/** Stock reservado; esperando que el cliente pague. */
		PENDIENTE_PAGO,
		PAGADO,
		/** El proveedor rechazó el pago: se puede reintentar con otro medio mientras dure la reserva. */
		PAGO_RECHAZADO,
		/** La farmacia está armando el pedido. */
		EN_PREPARACION,
		/** Retiro: el pedido espera al cliente en la farmacia. */
		LISTO_RETIRO,
		/** Despacho: el pedido salió del almacén central. */
		EN_CAMINO,
		ENTREGADO,
		/** No se pagó a tiempo y el stock volvió a estar disponible. */
		EXPIRADO,
		CANCELADO
	}

	public enum Entrega {
		/** Retiro en una farmacia elegida por el cliente. */
		RETIRO,
		/** Despacho a domicilio desde el almacén central. */
		DESPACHO
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usuario_id", nullable = false)
	private Long usuarioId;

	@Column(nullable = false)
	private Integer total;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Estado estado;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20)
	private Entrega entrega;

	@Column(name = "sucursal_id")
	private Long sucursalId;

	@Column(name = "sucursal_nombre", length = 100)
	private String sucursalNombre;

	@Column(length = 200)
	private String direccion;

	@Column(name = "costo_despacho", nullable = false)
	private Integer costoDespacho;

	@Column(name = "metodo_pago", length = 20)
	private String metodoPago;

	@Column(name = "reserva_id")
	private Long reservaId;

	@Column(name = "pago_id")
	private Long pagoId;

	@Column(name = "detalle_pago", length = 300)
	private String detallePago;

	@Column(name = "expira_en")
	private Instant expiraEn;

	@Column(name = "actualizado_en")
	private Instant actualizadoEn;

	/** Receta validada que respalda los productos de venta bajo receta del pedido. */
	@Column(name = "receta_id")
	private Long recetaId;

	@OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@Fetch(FetchMode.SUBSELECT)
	@OrderBy("id ASC")
	private List<PedidoItem> items = new ArrayList<>();

	@OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@Fetch(FetchMode.SUBSELECT)
	@OrderBy("fecha ASC, id ASC")
	private List<PedidoHistorial> historial = new ArrayList<>();

	/** Lo que el personal puede hacer con un pedido pagado, según cómo se entrega. */
	private static final Map<Estado, Set<Estado>> SIGUIENTES = Map.of(
			Estado.PAGADO, Set.of(Estado.EN_PREPARACION),
			Estado.EN_PREPARACION, Set.of(Estado.LISTO_RETIRO, Estado.EN_CAMINO),
			Estado.LISTO_RETIRO, Set.of(Estado.ENTREGADO),
			Estado.EN_CAMINO, Set.of(Estado.ENTREGADO));

	protected Pedido() {
	}

	public Pedido(Long usuarioId, Entrega entrega, String direccion, String metodoPago) {
		this.usuarioId = usuarioId;
		this.entrega = entrega;
		this.direccion = direccion;
		this.metodoPago = metodoPago;
		this.estado = Estado.PENDIENTE_PAGO;
		this.total = 0;
		this.costoDespacho = 0;
		this.creadoEn = Instant.now();
		this.actualizadoEn = this.creadoEn;
		registrar(Estado.PENDIENTE_PAGO, null, "Cliente");
	}

	private void registrar(Estado nuevo, String nota, String por) {
		historial.add(new PedidoHistorial(this, nuevo, nota, por, Instant.now()));
	}

	/**
	 * El personal avanza el pedido: en preparación → listo para retiro (retiro en farmacia) o en camino
	 * (despacho) → entregado.
	 */
	public void avanzar(Estado nuevo, String nota, String por) {
		boolean permitido = SIGUIENTES.getOrDefault(estado, Set.of()).contains(nuevo)
				&& !(nuevo == Estado.LISTO_RETIRO && entrega == Entrega.DESPACHO)
				&& !(nuevo == Estado.EN_CAMINO && entrega != Entrega.DESPACHO);
		if (!permitido) {
			throw new IllegalStateException("Un pedido " + estado + " no puede pasar a " + nuevo + ".");
		}
		this.estado = nuevo;
		registrar(nuevo, nota, por);
		tocar();
	}

	public void agregar(Long medicamentoId, String nombre, int precioUnitario, int cantidad) {
		items.add(new PedidoItem(this, medicamentoId, nombre, precioUnitario, cantidad));
		total += precioUnitario * cantidad;
	}

	public void asociarReceta(Long recetaId) {
		this.recetaId = recetaId;
	}

	public void cobrarDespacho(int costo) {
		total = total - costoDespacho + costo;
		costoDespacho = costo;
	}

	/** Referencia con que el inventario identifica la reserva de este pedido. */
	public String referencia() {
		return "PED-" + id;
	}

	public void reservado(Long reservaId, Long sucursalId, String sucursalNombre, Instant expiraEn) {
		this.reservaId = reservaId;
		this.sucursalId = sucursalId;
		this.sucursalNombre = sucursalNombre;
		this.expiraEn = expiraEn;
		tocar();
	}

	public void pagoIniciado(Long pagoId, String metodo) {
		this.pagoId = pagoId;
		this.metodoPago = metodo;
		this.estado = Estado.PENDIENTE_PAGO;
		this.detallePago = null;
		tocar();
	}

	/** El pago no se pudo iniciar (proveedor caído o circuito abierto): el cliente elige otro medio. */
	public void pagoNoIniciado(String metodo, String motivo) {
		this.metodoPago = metodo;
		this.detallePago = motivo;
		tocar();
	}

	public void pagado(Long pagoId, String metodo, String detalle) {
		this.pagoId = pagoId;
		this.metodoPago = metodo;
		this.estado = Estado.PAGADO;
		this.detallePago = detalle;
		registrar(Estado.PAGADO, null, "Sistema");
		tocar();
	}

	public void pagoRechazado(String motivo) {
		this.estado = Estado.PAGO_RECHAZADO;
		this.detallePago = motivo;
		registrar(Estado.PAGO_RECHAZADO, motivo, "Sistema");
		tocar();
	}

	public void expirar() {
		this.estado = Estado.EXPIRADO;
		this.detallePago = "No se completó el pago a tiempo y el stock se liberó.";
		registrar(Estado.EXPIRADO, null, "Sistema");
		tocar();
	}

	public void cancelar() {
		this.estado = Estado.CANCELADO;
		registrar(Estado.CANCELADO, null, "Cliente");
		tocar();
	}

	/** Se puede pagar (o reintentar) mientras esté pendiente o rechazado y la reserva siga vigente. */
	public boolean admitePago(Instant ahora) {
		return (estado == Estado.PENDIENTE_PAGO || estado == Estado.PAGO_RECHAZADO)
				&& (expiraEn == null || expiraEn.isAfter(ahora));
	}

	public boolean esDe(Long usuario) {
		return usuarioId.equals(usuario);
	}

	private void tocar() {
		this.actualizadoEn = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Integer getTotal() {
		return total;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Estado getEstado() {
		return estado;
	}

	public Entrega getEntrega() {
		return entrega;
	}

	public Long getSucursalId() {
		return sucursalId;
	}

	public String getSucursalNombre() {
		return sucursalNombre;
	}

	public String getDireccion() {
		return direccion;
	}

	public Integer getCostoDespacho() {
		return costoDespacho;
	}

	public String getMetodoPago() {
		return metodoPago;
	}

	public Long getReservaId() {
		return reservaId;
	}

	public Long getPagoId() {
		return pagoId;
	}

	public String getDetallePago() {
		return detallePago;
	}

	public Long getRecetaId() {
		return recetaId;
	}

	public Instant getExpiraEn() {
		return expiraEn;
	}

	public List<PedidoItem> getItems() {
		return items;
	}

	public List<PedidoHistorial> getHistorial() {
		return historial;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}
}
