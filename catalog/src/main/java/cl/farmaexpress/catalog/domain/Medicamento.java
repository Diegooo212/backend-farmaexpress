package cl.farmaexpress.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

	/** Unidades vendibles en todas las sucursales (lo que ve el cliente); {@code stock} es el máximo por pedido. */
	@Column(name = "stock_total", nullable = false)
	private Integer stockTotal = 0;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "categoria_id")
	private Categoria categoria;

	/** Para qué sirve y cómo se usa. */
	@Column(length = 1000)
	private String descripcion;

	@Column(length = 100)
	private String laboratorio;

	@Column(name = "principio_activo", length = 150)
	private String principioActivo;

	/** Formato de venta: "Caja 16 comprimidos", "Frasco 120 ml"... */
	@Column(length = 100)
	private String presentacion;

	/** Venta bajo receta médica: el checkout exige una receta validada. */
	@Column(name = "requiere_receta", nullable = false)
	private boolean requiereReceta;

	@Column(nullable = false)
	private boolean bioequivalente;

	@Column(name = "imagen_url", length = 500)
	private String imagenUrl;

	protected Medicamento() {
	}

	public Medicamento(String sku, String nombre, int precio, int stock) {
		actualizar(sku, nombre, precio, stock);
		this.stockTotal = stock;
	}

	public void actualizar(String sku, String nombre, int precio, int stock) {
		this.sku = sku.trim();
		this.nombre = nombre.trim();
		this.precio = precio;
		this.stock = stock;
	}

	/** Datos de la ficha del producto (todos opcionales salvo los booleanos). */
	public void actualizarFicha(Categoria categoria, String descripcion, String laboratorio, String principioActivo,
			String presentacion, boolean requiereReceta, boolean bioequivalente, String imagenUrl) {
		this.categoria = categoria;
		this.descripcion = limpio(descripcion);
		this.laboratorio = limpio(laboratorio);
		this.principioActivo = limpio(principioActivo);
		this.presentacion = limpio(presentacion);
		this.requiereReceta = requiereReceta;
		this.bioequivalente = bioequivalente;
		this.imagenUrl = limpio(imagenUrl);
	}

	private static String limpio(String valor) {
		return valor == null || valor.isBlank() ? null : valor.trim();
	}

	/**
	 * El stock lo informa el inventario: {@code porPedido} es lo que entrega la sucursal con más unidades
	 * y {@code total} la suma de todas las sucursales.
	 */
	public void fijarStock(int porPedido, int total) {
		this.stock = Math.max(0, porPedido);
		this.stockTotal = Math.max(this.stock, total);
	}

	public Integer getStockTotal() {
		return stockTotal;
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

	public Categoria getCategoria() {
		return categoria;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public String getLaboratorio() {
		return laboratorio;
	}

	public String getPrincipioActivo() {
		return principioActivo;
	}

	public String getPresentacion() {
		return presentacion;
	}

	public boolean isRequiereReceta() {
		return requiereReceta;
	}

	public boolean isBioequivalente() {
		return bioequivalente;
	}

	public String getImagenUrl() {
		return imagenUrl;
	}
}
