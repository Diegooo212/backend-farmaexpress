package cl.farmaexpress.bff.pedidos;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

	@EntityGraph(attributePaths = "items")
	List<Pedido> findAllByUsuarioIdOrderByCreadoEnDescIdDesc(Long usuarioId);

	/** ¿La receta ya respalda otro pedido vigente o pagado? (una receta sirve para una sola compra) */
	/** Panel del personal: pedidos en ciertos estados, los más antiguos primero (se atienden en orden). */
	List<Pedido> findAllByEstadoInOrderByCreadoEnAscIdAsc(java.util.Collection<Pedido.Estado> estados);

	boolean existsByRecetaIdAndEstadoIn(Long recetaId, java.util.Collection<Pedido.Estado> estados);
}
