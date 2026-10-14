package cl.farmaexpress.inventory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

	@EntityGraph(attributePaths = { "items", "items.lote", "sucursal" })
	Optional<Reserva> findWithItemsById(Long id);

	List<Reserva> findAllByEstadoAndExpiraEnBefore(Reserva.Estado estado, Instant limite);

	long countByEstado(Reserva.Estado estado);
}
