package cl.farmaexpress.prescriptions.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

/** Un cambio de estado de una receta: qué pasó, cuándo, quién lo hizo y el mensaje para el paciente. */
@Entity
@Table(name = "receta_historial")
public class HistorialEstado {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "receta_id", nullable = false)
	private Receta receta;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private EstadoReceta status;

	@Column(nullable = false)
	private Instant fecha;

	@Column(length = 300)
	private String nota;

	@Column(length = 150)
	private String por;

	protected HistorialEstado() {
	}

	HistorialEstado(Receta receta, EstadoReceta status, Instant fecha, String nota, String por) {
		this.receta = receta;
		this.status = status;
		this.fecha = fecha;
		this.nota = nota;
		this.por = por;
	}

	public Long getId() {
		return id;
	}

	public EstadoReceta getStatus() {
		return status;
	}

	public Instant getFecha() {
		return fecha;
	}

	public String getNota() {
		return nota;
	}

	public String getPor() {
		return por;
	}
}
