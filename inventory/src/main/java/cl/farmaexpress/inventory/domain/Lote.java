package cl.farmaexpress.inventory.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Un lote de un medicamento en una sucursal. Las unidades se reparten en:
 * <ul>
 * <li>{@code disponible}: se pueden vender;</li>
 * <li>{@code reservada}: apartadas para un pedido que se está pagando.</li>
 * </ul>
 * Un lote vencido nunca se vende, aunque tenga unidades.
 */
@Entity
@Table(name = "lotes")
public class Lote {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "medicamento_id", nullable = false)
	private Long medicamentoId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sucursal_id")
	private Sucursal sucursal;

	@Column(name = "numero_lote", nullable = false, length = 40)
	private String numeroLote;

	@Column(name = "fecha_vencimiento", nullable = false)
	private LocalDate fechaVencimiento;

	@Column(name = "cantidad_inicial", nullable = false)
	private int cantidadInicial;

	@Column(nullable = false)
	private int disponible;

	@Column(nullable = false)
	private int reservada;

	@Column(name = "ingresado_en", nullable = false)
	private Instant ingresadoEn;

	/** Bloqueo optimista: dos compras simultáneas no pueden tomar las mismas unidades. */
	@Version
	private long version;

	protected Lote() {
	}

	public Lote(Long medicamentoId, Sucursal sucursal, String numeroLote, LocalDate fechaVencimiento, int cantidad,
			Instant ahora) {
		this.medicamentoId = medicamentoId;
		this.sucursal = sucursal;
		this.numeroLote = numeroLote;
		this.fechaVencimiento = fechaVencimiento;
		this.cantidadInicial = cantidad;
		this.disponible = cantidad;
		this.reservada = 0;
		this.ingresadoEn = ahora;
	}

	public boolean estaVencido(LocalDate hoy) {
		return !fechaVencimiento.isAfter(hoy);
	}

	/** Aparta unidades para un pedido en curso. */
	public void reservar(int cantidad) {
		if (cantidad <= 0 || cantidad > disponible) {
			throw new IllegalStateException("El lote " + numeroLote + " no tiene " + cantidad + " unidades disponibles");
		}
		disponible -= cantidad;
		reservada += cantidad;
	}

	/** El pago se aprobó: las unidades reservadas salen del inventario. */
	public void confirmarSalida(int cantidad) {
		if (cantidad <= 0 || cantidad > reservada) {
			throw new IllegalStateException("El lote " + numeroLote + " no tiene " + cantidad + " unidades reservadas");
		}
		reservada -= cantidad;
	}

	/** El pedido no se pagó a tiempo o se canceló: las unidades vuelven a estar disponibles. */
	public void liberar(int cantidad) {
		if (cantidad <= 0 || cantidad > reservada) {
			throw new IllegalStateException("El lote " + numeroLote + " no tiene " + cantidad + " unidades reservadas");
		}
		reservada -= cantidad;
		disponible += cantidad;
	}

	/** Baja por vencimiento, daño o pérdida. */
	public void darDeBaja(int cantidad) {
		if (cantidad <= 0 || cantidad > disponible) {
			throw new IllegalStateException("El lote " + numeroLote + " no tiene " + cantidad + " unidades disponibles");
		}
		disponible -= cantidad;
	}

	public Long getId() {
		return id;
	}

	public Long getMedicamentoId() {
		return medicamentoId;
	}

	public Sucursal getSucursal() {
		return sucursal;
	}

	public String getNumeroLote() {
		return numeroLote;
	}

	public LocalDate getFechaVencimiento() {
		return fechaVencimiento;
	}

	public int getCantidadInicial() {
		return cantidadInicial;
	}

	public int getDisponible() {
		return disponible;
	}

	public int getReservada() {
		return reservada;
	}

	public Instant getIngresadoEn() {
		return ingresadoEn;
	}
}
