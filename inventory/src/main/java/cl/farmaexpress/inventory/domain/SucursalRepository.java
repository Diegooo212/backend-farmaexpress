package cl.farmaexpress.inventory.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SucursalRepository extends JpaRepository<Sucursal, Long> {

	List<Sucursal> findAllByOrderByIdAsc();

	Optional<Sucursal> findFirstByTipoOrderByIdAsc(Sucursal.Tipo tipo);
}
