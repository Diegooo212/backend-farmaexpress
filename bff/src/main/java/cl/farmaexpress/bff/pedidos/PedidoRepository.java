package cl.farmaexpress.bff.pedidos;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

	@EntityGraph(attributePaths = "items")
	List<Pedido> findAllByUsuarioIdOrderByCreadoEnDescIdDesc(Long usuarioId);
}
