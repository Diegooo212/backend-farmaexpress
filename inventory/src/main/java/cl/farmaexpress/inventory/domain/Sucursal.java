package cl.farmaexpress.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sucursales")
public class Sucursal {

	public enum Tipo {
		/** Almacén central: abastece los despachos a domicilio. */
		CENTRAL,
		/** Farmacia: el cliente puede retirar ahí. */
		FARMACIA
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String codigo;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(nullable = false, length = 200)
	private String direccion;

	@Column(nullable = false, length = 80)
	private String comuna;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Tipo tipo;

	protected Sucursal() {
	}

	public Long getId() {
		return id;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getNombre() {
		return nombre;
	}

	public String getDireccion() {
		return direccion;
	}

	public String getComuna() {
		return comuna;
	}

	public Tipo getTipo() {
		return tipo;
	}
}
