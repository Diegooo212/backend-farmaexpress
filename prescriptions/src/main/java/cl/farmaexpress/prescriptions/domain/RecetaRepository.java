package cl.farmaexpress.prescriptions.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecetaRepository extends JpaRepository<Receta, Long> {

	// El historial se trae en la misma consulta para no hacer una consulta por receta.

	@EntityGraph(attributePaths = "historial")
	List<Receta> findAllByOrderByCreadaEnDescIdDesc();

	@EntityGraph(attributePaths = "historial")
	List<Receta> findAllByStatusOrderByCreadaEnDescIdDesc(EstadoReceta status);

	@EntityGraph(attributePaths = "historial")
	List<Receta> findAllByCuentaIdOrderByCreadaEnDescIdDesc(String cuentaId);

	@EntityGraph(attributePaths = "historial")
	List<Receta> findAllByCuentaIdAndStatusOrderByCreadaEnDescIdDesc(String cuentaId, EstadoReceta status);

	@EntityGraph(attributePaths = "historial")
	Optional<Receta> findWithHistorialById(Long id);
}
