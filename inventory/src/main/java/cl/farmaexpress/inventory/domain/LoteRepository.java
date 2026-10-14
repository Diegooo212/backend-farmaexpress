package cl.farmaexpress.inventory.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoteRepository extends JpaRepository<Lote, Long> {

	/**
	 * Regla FEFO (First Expired, First Out): lotes vendibles de un producto en una sucursal, del que
	 * vence primero al que vence último. Se bloquean mientras dura la reserva para que dos compras
	 * simultáneas no tomen las mismas unidades.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select l from Lote l
			where l.medicamentoId = :medicamentoId and l.sucursal.id = :sucursalId
			  and l.disponible > 0 and l.fechaVencimiento > :hoy
			order by l.fechaVencimiento asc, l.id asc""")
	List<Lote> findVendiblesFefo(@Param("medicamentoId") Long medicamentoId, @Param("sucursalId") Long sucursalId,
			@Param("hoy") LocalDate hoy);

	/** Unidades vendibles (no vencidas) por producto y sucursal. */
	@Query("""
			select new cl.farmaexpress.inventory.domain.StockPorSucursal(l.medicamentoId, l.sucursal.id, sum(l.disponible))
			from Lote l
			where l.medicamentoId in :medicamentos and l.fechaVencimiento > :hoy
			group by l.medicamentoId, l.sucursal.id""")
	List<StockPorSucursal> stockVendible(@Param("medicamentos") Collection<Long> medicamentos, @Param("hoy") LocalDate hoy);

	boolean existsByMedicamentoIdAndSucursal_IdAndNumeroLoteIgnoreCase(Long medicamentoId, Long sucursalId, String numeroLote);

	@Query("select distinct l.medicamentoId from Lote l")
	List<Long> medicamentosConLotes();

	@EntityGraph(attributePaths = "sucursal")
	@Query("select l from Lote l order by l.fechaVencimiento asc, l.id asc")
	List<Lote> findAllFefo();
}
