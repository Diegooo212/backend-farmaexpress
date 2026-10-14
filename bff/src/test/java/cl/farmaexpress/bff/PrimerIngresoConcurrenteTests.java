package cl.farmaexpress.bff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import cl.farmaexpress.bff.auth.UsuarioActual;
import cl.farmaexpress.bff.auth.UsuarioRepository;
import cl.farmaexpress.bff.auth.UsuarioService;

/**
 * Al iniciar sesión el frontend pide perfil, carrito y pedidos a la vez: todas esas peticiones
 * sincronizan el mismo usuario nuevo al mismo tiempo y ninguna debe fallar. Sin @Transactional
 * (cada hilo usa su propia transacción) y con su propia base H2.
 */
@SpringBootTest
@ActiveProfiles({ "h2", "test" })
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:bff-concurrencia;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class PrimerIngresoConcurrenteTests {

	@Autowired
	UsuarioService usuarios;

	@Autowired
	UsuarioRepository repositorio;

	@Test
	void variasPeticionesSimultaneasDelPrimerIngresoCreanUnSoloPerfil() throws Exception {
		UsuarioActual diego = new UsuarioActual("oid-simultaneo", "simultaneo@test.cl", "Diego", List.of("Cliente"),
				List.of("access_as_user"));
		int peticiones = 8;
		ExecutorService hilos = Executors.newFixedThreadPool(peticiones);
		CountDownLatch largada = new CountDownLatch(1);
		List<Future<Long>> resultados = new ArrayList<>();
		for (int i = 0; i < peticiones; i++) {
			Callable<Long> tarea = () -> {
				largada.await();
				return usuarios.sincronizar(diego).getId();
			};
			resultados.add(hilos.submit(tarea));
		}
		largada.countDown();

		List<Long> ids = new ArrayList<>();
		for (Future<Long> r : resultados) {
			ids.add(r.get()); // Si alguna petición falló, aquí se lanza la excepción.
		}
		hilos.shutdown();

		assertThat(ids).containsOnly(ids.get(0));
		assertThat(repositorio.findByEntraOid("oid-simultaneo")).isPresent();
		assertThat(repositorio.findAll().stream().filter(u -> "simultaneo@test.cl".equals(u.getEmail()))).hasSize(1);
	}
}
