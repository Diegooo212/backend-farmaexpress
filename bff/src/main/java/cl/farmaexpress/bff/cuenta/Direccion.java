package cl.farmaexpress.bff.cuenta;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Dirección de despacho guardada por el cliente ("Casa", "Trabajo"...). */
@Entity
@Table(name = "direcciones")
public class Direccion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usuario_id", nullable = false)
	private Long usuarioId;

	@Column(nullable = false, length = 40)
	private String alias;

	@Column(nullable = false, length = 120)
	private String calle;

	@Column(length = 30)
	private String depto;

	@Column(nullable = false, length = 40)
	private String comuna;

	@Column(nullable = false)
	private boolean predeterminada;

	@Column(name = "creada_en", nullable = false)
	private Instant creadaEn;

	protected Direccion() {
	}

	public Direccion(Long usuarioId) {
		this.usuarioId = usuarioId;
		this.creadaEn = Instant.now();
	}

	public void actualizar(String alias, String calle, String depto, String comuna) {
		this.alias = alias.trim();
		this.calle = calle.trim();
		this.depto = depto == null || depto.isBlank() ? null : depto.trim();
		this.comuna = comuna.trim();
	}

	public void marcarPredeterminada(boolean valor) {
		this.predeterminada = valor;
	}

	/** Texto para el pedido: "Av. Providencia 1234, depto 501, Providencia". */
	public String completa() {
		return depto == null ? calle + ", " + comuna : calle + ", " + depto + ", " + comuna;
	}

	public Long getId() {
		return id;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getAlias() {
		return alias;
	}

	public String getCalle() {
		return calle;
	}

	public String getDepto() {
		return depto;
	}

	public String getComuna() {
		return comuna;
	}

	public boolean isPredeterminada() {
		return predeterminada;
	}
}
