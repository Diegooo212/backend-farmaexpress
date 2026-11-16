package cl.farmaexpress.catalog.domain;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicamentoRepository extends JpaRepository<Medicamento, Long> {

	List<Medicamento> findAllByOrderByIdAsc();

	boolean existsBySkuIgnoreCase(String sku);

	boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

	/** Bloquea las filas mientras se descuenta stock, para que dos compras simultáneas no vendan de más. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Medicamento m where m.id in :ids")
	List<Medicamento> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

	/**
	 * Suma unidades vendidas en una sola sentencia UPDATE (atómica en la base): dos consumidores de Kafka que
	 * procesan ventas del mismo producto en paralelo nunca pisan el valor del otro.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Medicamento m set m.vendidos = m.vendidos + :unidades where m.id = :id")
	int sumarVendidos(@Param("id") Long id, @Param("unidades") int unidades);
}
