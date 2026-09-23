package cl.farmaexpress.prescriptions;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import cl.farmaexpress.prescriptions.config.EntraJwtConfig;

/**
 * Simula a Entra ID en las pruebas: firma tokens con una llave RSA propia y el decoder de la API
 * usa esa llave pública, pero con las mismas reglas de producción (issuer, audience, vigencia).
 */
@TestConfiguration
public class EntraDePrueba {

	public static final String TENANT = "11111111-2222-3333-4444-555555555555";
	public static final String API_CLIENT_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
	public static final String ISSUER = "https://" + TENANT + ".ciamlogin.com/" + TENANT + "/v2.0";
	private static final KeyPair LLAVES = generarLlaves();

	@Bean
	@Primary
	JwtDecoder jwtDecoderDePrueba() {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) LLAVES.getPublic()).build();
		decoder.setJwtValidator(EntraJwtConfig.validador(ISSUER, API_CLIENT_ID));
		return decoder;
	}

	/** Access token como los de Entra External ID; {@code cambios} reemplaza o quita (null) claims. */
	public static String token(Map<String, Object> cambios) {
		RSAKey jwk = new RSAKey.Builder((RSAPublicKey) LLAVES.getPublic()).privateKey((RSAPrivateKey) LLAVES.getPrivate()).build();
		NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
		Instant ahora = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.audience(List.of(API_CLIENT_ID))
			.subject("sujeto-de-prueba")
			.issuedAt(ahora)
			.notBefore(ahora)
			.expiresAt(ahora.plusSeconds(3600))
			.claim("oid", "oid-de-prueba")
			.claim("name", "Persona de Prueba")
			.claim("email", "persona@test.cl")
			.claim("scp", "access_as_user");
		cambios.forEach((clave, valor) -> {
			if (valor == null) {
				claims.claims(c -> c.remove(clave));
			}
			else {
				claims.claim(clave, valor);
			}
		});
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
			.getTokenValue();
	}

	private static KeyPair generarLlaves() {
		try {
			KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
			generador.initialize(2048);
			return generador.generateKeyPair();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
