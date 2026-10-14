package cl.farmaexpress.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Categoría de la tienda (Dolor y fiebre, Alergias...). Se cargan con Flyway. */
@Entity
@Table(name = "categorias")
public class Categoria {

	@Id
	private Long id;

	@Column(nullable = false, length = 40)
	private String slug;

	@Column(nullable = false, length = 60)
	private String nombre;

	@Column(nullable = false, length = 200)
	private String descripcion;

	/** Nombre del ícono que dibuja el frontend. */
	@Column(nullable = false, length = 40)
	private String icono;

	@Column(nullable = false)
	private Integer orden;

	protected Categoria() {
	}

	public Long getId() {
		return id;
	}

	public String getSlug() {
		return slug;
	}

	public String getNombre() {
		return nombre;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public String getIcono() {
		return icono;
	}

	public Integer getOrden() {
		return orden;
	}
}
