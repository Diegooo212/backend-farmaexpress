package cl.farmaexpress.inventory.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

	List<Movimiento> findAllByLoteIdOrderByFechaDescIdDesc(Long loteId);
}
