package cl.farmaexpress.prescriptions.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** Contenido binario de la receta (foto o PDF), guardado aparte de los datos de la receta. */
@Entity
@Table(name = "receta_archivos")
public class RecetaArchivo {

	@Id
	@Column(name = "receta_id")
	private Long recetaId;

	@Lob
	@Basic(fetch = FetchType.LAZY)
	@Column(nullable = false, columnDefinition = "LONGBLOB")
	private byte[] contenido;

	protected RecetaArchivo() {
	}

	public RecetaArchivo(Long recetaId, byte[] contenido) {
		this.recetaId = recetaId;
		this.contenido = contenido;
	}

	public Long getRecetaId() {
		return recetaId;
	}

	public byte[] getContenido() {
		return contenido;
	}
}
