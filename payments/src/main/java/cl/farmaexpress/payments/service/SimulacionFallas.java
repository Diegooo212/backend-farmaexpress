package cl.farmaexpress.payments.service;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import cl.farmaexpress.payments.domain.MetodoPago;

/**
 * "Apaga" un proveedor a propósito (solo el Administrador, desde la API) para demostrar el circuit
 * breaker sin tener que desconectar la red: sus llamadas fallan como si el proveedor no respondiera.
 */
@Component
public class SimulacionFallas {

	private final Set<MetodoPago> caidos = EnumSet.noneOf(MetodoPago.class);

	public synchronized boolean estaCaido(MetodoPago metodo) {
		return caidos.contains(metodo);
	}

	public synchronized void cambiar(MetodoPago metodo, boolean caido) {
		if (caido) {
			caidos.add(metodo);
		}
		else {
			caidos.remove(metodo);
		}
	}
}
