package cl.farmaexpress.payments.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pagos")
public class Pago {

	public enum Estado {
		/** Esperando que el cliente pague en el proveedor. */
		PENDIENTE,
		APROBADO,
		RECHAZADO,
		/** No se pudo iniciar con el proveedor (caído o circuito abierto). */
		ERROR;

		public boolean esFinal() {
			return this != PENDIENTE;
		}
	}

	/** REAL: se usa la API del proveedor (ambiente de pruebas o producción). SIMULADO: simulador propio. */
	public enum Modo {
		REAL, SIMULADO
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "pedido_id", nullable = false)
	private Long pedidoId;

	@Column(name = "reserva_id")
	private Long reservaId;

	@Column(name = "cuenta_id", nullable = false, length = 64)
	private String cuentaId;

	@Column(nullable = false)
	private int monto;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private MetodoPago metodo;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Modo modo;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Estado estado;

	@Column(name = "token_proveedor", length = 120)
	private String tokenProveedor;

	@Column(name = "token_simulador", length = 64)
	private String tokenSimulador;

	@Column(name = "referencia_proveedor", length = 120)
	private String referenciaProveedor;

	@Column(length = 300)
	private String detalle;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	@Version
	private long version;

	protected Pago() {
	}

	public Pago(Long pedidoId, Long reservaId, String cuentaId, int monto, MetodoPago metodo, Modo modo,
			String tokenSimulador, Instant ahora) {
		this.pedidoId = pedidoId;
		this.reservaId = reservaId;
		this.cuentaId = cuentaId;
		this.monto = monto;
		this.metodo = metodo;
		this.modo = modo;
		this.tokenSimulador = tokenSimulador;
		this.estado = Estado.PENDIENTE;
		this.creadoEn = ahora;
		this.actualizadoEn = ahora;
	}

	/** Orden de compra que se informa al proveedor (Webpay acepta hasta 26 caracteres). */
	public String ordenCompra() {
		return "FX" + id;
	}

	public void iniciado(String tokenProveedor, Instant ahora) {
		this.tokenProveedor = tokenProveedor;
		this.actualizadoEn = ahora;
	}

	public void aprobar(String referencia, String detalle, Instant ahora) {
		this.estado = Estado.APROBADO;
		this.referenciaProveedor = referencia;
		this.detalle = detalle;
		this.actualizadoEn = ahora;
	}

	public void rechazar(String detalle, Instant ahora) {
		this.estado = Estado.RECHAZADO;
		this.detalle = detalle;
		this.actualizadoEn = ahora;
	}

	public void fallar(String detalle, Instant ahora) {
		this.estado = Estado.ERROR;
		this.detalle = detalle;
		this.actualizadoEn = ahora;
	}

	public boolean perteneceA(String cuenta) {
		return cuentaId.equals(cuenta);
	}

	public Long getId() {
		return id;
	}

	public Long getPedidoId() {
		return pedidoId;
	}

	public Long getReservaId() {
		return reservaId;
	}

	public String getCuentaId() {
		return cuentaId;
	}

	public int getMonto() {
		return monto;
	}

	public MetodoPago getMetodo() {
		return metodo;
	}

	public Modo getModo() {
		return modo;
	}

	public Estado getEstado() {
		return estado;
	}

	public String getTokenProveedor() {
		return tokenProveedor;
	}

	public String getTokenSimulador() {
		return tokenSimulador;
	}

	public String getReferenciaProveedor() {
		return referenciaProveedor;
	}

	public String getDetalle() {
		return detalle;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}
}
