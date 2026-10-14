package cl.farmaexpress.bff.pedidos;

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

/** Un paso en el seguimiento del pedido. */
@Entity
@Table(name = "pedido_historial")
public class PedidoHistorial {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pedido_id")
	private Pedido pedido;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Pedido.Estado estado;

	@Column(nullable = false)
	private Instant fecha;

	@Column(length = 300)
	private String nota;

	@Column(length = 150)
	private String por;

	protected PedidoHistorial() {
	}

	PedidoHistorial(Pedido pedido, Pedido.Estado estado, String nota, String por, Instant fecha) {
		this.pedido = pedido;
		this.estado = estado;
		this.nota = nota;
		this.por = por;
		this.fecha = fecha;
	}

	public Pedido.Estado getEstado() {
		return estado;
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
