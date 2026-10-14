package cl.farmaexpress.bff.cuenta;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DireccionRepository extends JpaRepository<Direccion, Long> {

	List<Direccion> findAllByUsuarioIdOrderByPredeterminadaDescIdAsc(Long usuarioId);

	long countByUsuarioId(Long usuarioId);
}
