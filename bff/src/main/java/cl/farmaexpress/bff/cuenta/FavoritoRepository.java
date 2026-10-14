package cl.farmaexpress.bff.cuenta;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoritoRepository extends JpaRepository<Favorito, Favorito.Clave> {

	/** Los más recientes primero. */
	List<Favorito> findAllByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);

	long countByUsuarioId(Long usuarioId);
}
