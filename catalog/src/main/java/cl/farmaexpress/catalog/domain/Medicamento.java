package cl.farmaexpress.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "medicamentos")
public class Medicamento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 40)
	private String sku;

	@Column(nullable = false, length = 150)
	private String nombre;

	/** Precio en pesos chilenos (sin decimales). */
	@Column(nullable = false)
	private Integer precio;

	@Column(nullable = false)
	private Integer stock;

	protected Medicamento() {
	}

	public Medicamento(String sku, String nombre, int precio, int stock) {
		actualizar(sku, nombre, precio, stock);
	}

	public void actualizar(String sku, String nombre, int precio, int stock) {
		this.sku = sku.trim();
		this.nombre = nombre.trim();
		this.precio = precio;
		this.stock = stock;
	}

	public boolean tieneStock(int cantidad) {
		return stock >= cantidad;
	}

	public void descontarStock(int cantidad) {
		if (!tieneStock(cantidad)) {
			throw new IllegalStateException("Stock insuficiente de " + nombre);
		}
		this.stock -= cantidad;
	}

	public Long getId() {
		return id;
	}

	public String getSku() {
		return sku;
	}

	public String getNombre() {
		return nombre;
	}

	public Integer getPrecio() {
		return precio;
	}

	public Integer getStock() {
		return stock;
	}
}
