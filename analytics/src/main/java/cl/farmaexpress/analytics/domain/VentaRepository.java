package cl.farmaexpress.analytics.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaRepository extends JpaRepository<Venta, Long> {

	/** [cantidad de ventas, ingresos, unidades] desde una fecha. */
	@Query("select count(v), coalesce(sum(v.total), 0), coalesce(sum(v.unidades), 0) from Venta v where v.fecha >= :desde")
	List<Object[]> totalesDesde(@Param("desde") Instant desde);

	List<Venta> findByFechaGreaterThanEqual(Instant desde);

	List<Venta> findAllByOrderByRecibidaEnDesc(Pageable pagina);

	/** [sucursal, ventas, ingresos]; sucursal es null cuando el pedido salió del almacén central (despacho). */
	@Query("select v.sucursalNombre, count(v), sum(v.total) from Venta v group by v.sucursalNombre order by sum(v.total) desc")
	List<Object[]> porSucursal();

	/** [método de pago, ventas, ingresos]. */
	@Query("select v.metodoPago, count(v), sum(v.total) from Venta v group by v.metodoPago order by count(v) desc")
	List<Object[]> porMetodoPago();

	/** [id del producto, nombre, unidades, ingresos], de lo más vendido a lo menos. */
	@Query("select i.medicamentoId, max(i.nombre), sum(i.cantidad), sum(i.subtotal) from VentaItem i "
			+ "group by i.medicamentoId order by sum(i.cantidad) desc")
	List<Object[]> masVendidos(Pageable pagina);
}
