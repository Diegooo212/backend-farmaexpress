package cl.farmaexpress.inventory.domain;

import java.time.Instant;

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
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Registro de trazabilidad: qué entró o salió de un lote, cuándo, por qué y quién. */
@Entity
@Table(name = "movimientos")
public class Movimiento {

	public enum Tipo {
		INGRESO, RESERVA, LIBERACION, SALIDA, MERMA
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lote_id")
	private Lote lote;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Tipo tipo;

	@Column(nullable = false)
	private int cantidad;

	@Column(length = 150)
	private String referencia;

	@Column(length = 150)
	private String usuario;

	@Column(nullable = false)
	private Instant fecha;

	protected Movimiento() {
	}

	public Movimiento(Lote lote, Tipo tipo, int cantidad, String referencia, String usuario, Instant fecha) {
		this.lote = lote;
		this.tipo = tipo;
		this.cantidad = cantidad;
		this.referencia = referencia;
		this.usuario = usuario;
		this.fecha = fecha;
	}

	public Long getId() {
		return id;
	}

	public Tipo getTipo() {
		return tipo;
	}

	public int getCantidad() {
		return cantidad;
	}

	public String getReferencia() {
		return referencia;
	}

	public String getUsuario() {
		return usuario;
	}

	public Instant getFecha() {
		return fecha;
	}
}
