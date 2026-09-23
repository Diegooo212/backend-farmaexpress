package cl.farmaexpress.prescriptions.domain;

/**
 * Recorrido de una receta. El orden normal es
 * INGRESADA → VALIDADA → EN_PREPARACION → LISTA_RETIRO → DISPENSADA;
 * RECHAZADA solo es posible antes de empezar a prepararla.
 */
public enum EstadoReceta {
	INGRESADA,
	VALIDADA,
	EN_PREPARACION,
	LISTA_RETIRO,
	DISPENSADA,
	RECHAZADA;

	public EstadoReceta siguiente() {
		return switch (this) {
			case INGRESADA -> VALIDADA;
			case VALIDADA -> EN_PREPARACION;
			case EN_PREPARACION -> LISTA_RETIRO;
			case LISTA_RETIRO -> DISPENSADA;
			case DISPENSADA, RECHAZADA -> null;
		};
	}

	public boolean puedeRechazarse() {
		return this == INGRESADA || this == VALIDADA;
	}

	public boolean puedePasarA(EstadoReceta nuevo) {
		if (nuevo == RECHAZADA) {
			return puedeRechazarse();
		}
		return nuevo != null && nuevo == siguiente();
	}
}
