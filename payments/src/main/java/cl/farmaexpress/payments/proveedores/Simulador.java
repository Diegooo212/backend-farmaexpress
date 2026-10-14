package cl.farmaexpress.payments.proveedores;

import java.util.Map;

import org.springframework.web.util.UriComponentsBuilder;

import cl.farmaexpress.payments.domain.Pago;
import cl.farmaexpress.payments.proveedores.ProveedorPago.Redireccion;
import cl.farmaexpress.payments.proveedores.ProveedorPago.Resultado;

/**
 * Pasarela de pruebas para los proveedores sin credenciales: el cliente va a la página
 * /pago/simulador del frontend, elige "Aprobar" o "Rechazar" y vuelve con el secreto del pago.
 */
public final class Simulador {

	private Simulador() {
	}

	public static Redireccion iniciar(Pago pago, String urlFrontend, String urlRetorno) {
		String url = UriComponentsBuilder.fromUriString(urlFrontend + "/pago/confirmar")
			.queryParam("pago", pago.getId())
			.queryParam("metodo", pago.getMetodo().name())
			.queryParam("monto", pago.getMonto())
			.queryParam("token", pago.getTokenSimulador())
			.queryParam("retorno", urlRetorno)
			.encode()
			.toUriString();
		return Redireccion.get(url, "SIM-" + pago.getId());
	}

	public static Resultado confirmar(Pago pago, Map<String, String> parametros) {
		// Sin el secreto correcto no se toca el pago: podría ser alguien probando ids ajenos.
		if (pago.getTokenSimulador() == null || !pago.getTokenSimulador().equals(parametros.get("token"))) {
			throw new IllegalArgumentException("El token del simulador no corresponde al pago " + pago.getId());
		}
		if ("aprobado".equals(parametros.get("resultado"))) {
			return Resultado.aprobado("SIM-" + pago.getId(), "Pago aprobado con " + pago.getMetodo().nombre() + ".");
		}
		return Resultado.rechazado("Pago rechazado por " + pago.getMetodo().nombre() + ".");
	}
}
