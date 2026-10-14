package cl.farmaexpress.inventory.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Unidades apartadas (lote por lote, según FEFO) para un pedido mientras el cliente paga. */
@Entity
@Table(name = "reservas")
public class Reserva {

	public enum Estado {
		ACTIVA, CONFIRMADA, LIBERADA, EXPIRADA
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Pedido del BFF al que pertenece. */
	@Column(nullable = false, length = 60)
	private String referencia;

	@Column(name = "cuenta_id", length = 64)
	private String cuentaId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sucursal_id")
	private Sucursal sucursal;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Estado estado;

	@Column(name = "creada_en", nullable = false)
	private Instant creadaEn;

	@Column(name = "expira_en", nullable = false)
	private Instant expiraEn;

	@OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ReservaItem> items = new ArrayList<>();

	protected Reserva() {
	}

	public Reserva(String referencia, String cuentaId, Sucursal sucursal, Instant ahora, Instant expiraEn) {
		this.referencia = referencia;
		this.cuentaId = cuentaId;
		this.sucursal = sucursal;
		this.estado = Estado.ACTIVA;
		this.creadaEn = ahora;
		this.expiraEn = expiraEn;
	}

	public void agregar(Lote lote, int cantidad) {
		items.add(new ReservaItem(this, lote, cantidad));
	}

	public boolean estaActiva() {
		return estado == Estado.ACTIVA;
	}

	public void cambiarEstado(Estado nuevo) {
		this.estado = nuevo;
	}

	public boolean perteneceA(String cuenta) {
		return cuentaId != null && cuentaId.equals(cuenta);
	}

	public Long getId() {
		return id;
	}

	public String getReferencia() {
		return referencia;
	}

	public String getCuentaId() {
		return cuentaId;
	}

	public Sucursal getSucursal() {
		return sucursal;
	}

	public Estado getEstado() {
		return estado;
	}

	public Instant getCreadaEn() {
		return creadaEn;
	}

	public Instant getExpiraEn() {
		return expiraEn;
	}

	public List<ReservaItem> getItems() {
		return items;
	}
}
