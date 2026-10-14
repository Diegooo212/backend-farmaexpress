package cl.farmaexpress.bff.cuenta;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Producto marcado como favorito por un cliente. */
@Entity
@Table(name = "favoritos")
@IdClass(Favorito.Clave.class)
public class Favorito {

	public static class Clave implements Serializable {

		private Long usuarioId;
		private Long medicamentoId;

		public Clave() {
		}

		public Clave(Long usuarioId, Long medicamentoId) {
			this.usuarioId = usuarioId;
			this.medicamentoId = medicamentoId;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Clave c && Objects.equals(usuarioId, c.usuarioId)
					&& Objects.equals(medicamentoId, c.medicamentoId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(usuarioId, medicamentoId);
		}
	}

	@Id
	@Column(name = "usuario_id")
	private Long usuarioId;

	@Id
	@Column(name = "medicamento_id")
	private Long medicamentoId;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;

	protected Favorito() {
	}

	public Favorito(Long usuarioId, Long medicamentoId) {
		this.usuarioId = usuarioId;
		this.medicamentoId = medicamentoId;
		this.creadoEn = Instant.now();
	}

	public Long getMedicamentoId() {
		return medicamentoId;
	}
}
