package cl.farmaexpress.analytics.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockProductoRepository extends JpaRepository<StockProducto, Long> {

	List<StockProducto> findByStockTotalLessThanEqualOrderByStockTotalAscMedicamentoIdAsc(int umbral);
}
