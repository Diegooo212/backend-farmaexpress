package cl.farmaexpress.notifications.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Aviso para un cliente, creado a partir de un evento de otro microservicio. */
@Entity
@Table(name = "notificaciones")
public class Notificacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Id del evento de RabbitMQ que la originó: único, para no duplicar avisos si el mensaje llega dos veces. */
	@Column(name = "evento_id", nullable = false, unique = true, length = 64)
	private String eventoId;

	/** oid de Entra ID del destinatario. */
	@Column(name = "cuenta_id", nullable = false, length = 64)
	private String cuentaId;

	/** RECETA o PEDIDO. */
	@Column(nullable = false, length = 20)
	private String tipo;

	@Column(name = "referencia_id")
	private Long referenciaId;

	/** Estado de la receta (VALIDADA, RECHAZADA...) para que el frontend le dé color; null en pedidos. */
	@Column(length = 30)
	private String estado;

	@Column(nullable = false, length = 150)
	private String titulo;

	@Column(nullable = false, length = 255)
	private String mensaje;

	@Column(length = 500)
	private String detalle;

	@Column(nullable = false)
	private boolean leida;

	@Column(nullable = false)
	private Instant fecha;

	protected Notificacion() {
	}

	public Notificacion(String eventoId, String cuentaId, String tipo, Long referenciaId, String estado, String titulo,
			String mensaje, String detalle, Instant fecha) {
		this.eventoId = eventoId;
		this.cuentaId = cuentaId;
		this.tipo = tipo;
		this.referenciaId = referenciaId;
		this.estado = estado;
		this.titulo = titulo;
		this.mensaje = mensaje;
		this.detalle = detalle;
		this.fecha = fecha;
		this.leida = false;
	}

	public void marcarLeida() {
		this.leida = true;
	}

	public Long getId() {
		return id;
	}

	public String getEventoId() {
		return eventoId;
	}

	public String getCuentaId() {
		return cuentaId;
	}

	public String getTipo() {
		return tipo;
	}

	public Long getReferenciaId() {
		return referenciaId;
	}

	public String getEstado() {
		return estado;
	}

	public String getTitulo() {
		return titulo;
	}

	public String getMensaje() {
		return mensaje;
	}

	public String getDetalle() {
		return detalle;
	}

	public boolean isLeida() {
		return leida;
	}

	public Instant getFecha() {
		return fecha;
	}
}
