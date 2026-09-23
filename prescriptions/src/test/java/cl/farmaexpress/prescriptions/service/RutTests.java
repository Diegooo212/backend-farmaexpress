package cl.farmaexpress.prescriptions.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RutTests {

	@ParameterizedTest
	@ValueSource(strings = { "11.111.111-1", "12.345.678-5", "9.876.543-3", "15.555.555-6", "12345678-5", "10.000.013-K", "10000013k" })
	void rutsValidos(String rut) {
		assertThat(Rut.esValido(rut)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "11.111.111-2", "12.345.678-9", "", "1", "abc", "12.3A5.678-5" })
	void rutsInvalidos(String rut) {
		assertThat(Rut.esValido(rut)).isFalse();
	}
}
