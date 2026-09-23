package cl.farmaexpress.prescriptions.service;

/** Validación del RUT chileno (módulo 11), igual que la del frontend. */
public final class Rut {

	private Rut() {
	}

	public static boolean esValido(String rut) {
		if (rut == null) {
			return false;
		}
		String limpio = rut.replaceAll("[^0-9kK]", "").toUpperCase();
		if (limpio.length() < 2) {
			return false;
		}
		String cuerpo = limpio.substring(0, limpio.length() - 1);
		char dv = limpio.charAt(limpio.length() - 1);
		if (!cuerpo.chars().allMatch(Character::isDigit)) {
			return false;
		}

		int suma = 0;
		int multiplicador = 2;
		for (int i = cuerpo.length() - 1; i >= 0; i--) {
			suma += (cuerpo.charAt(i) - '0') * multiplicador;
			multiplicador = multiplicador == 7 ? 2 : multiplicador + 1;
		}
		int resto = 11 - (suma % 11);
		char esperado = resto == 11 ? '0' : resto == 10 ? 'K' : (char) ('0' + resto);
		return dv == esperado;
	}
}
