package cl.farmaexpress.payments.domain;

/** Medios de pago que ofrece FarmaExpress. */
public enum MetodoPago {

	WEBPAY("Webpay", "Tarjetas de crédito, débito y prepago (Transbank)"),
	MERCADOPAGO("Mercado Pago", "Paga con tu cuenta Mercado Pago o tus tarjetas guardadas"),
	KHIPU("Khipu", "Transferencia bancaria simplificada, sin salir de la tienda");

	private final String nombre;
	private final String descripcion;

	MetodoPago(String nombre, String descripcion) {
		this.nombre = nombre;
		this.descripcion = descripcion;
	}

	public String nombre() {
		return nombre;
	}

	public String descripcion() {
		return descripcion;
	}

	/** Segmento de la URL de retorno: /retorno/webpay, /retorno/mercadopago, /retorno/khipu. */
	public String ruta() {
		return name().toLowerCase();
	}

	public static MetodoPago desdeRuta(String ruta) {
		for (MetodoPago m : values()) {
			if (m.ruta().equalsIgnoreCase(ruta)) {
				return m;
			}
		}
		throw new IllegalArgumentException("Método de pago desconocido: " + ruta);
	}
}
