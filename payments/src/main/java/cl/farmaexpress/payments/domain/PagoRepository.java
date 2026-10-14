package cl.farmaexpress.payments.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {

	boolean existsByPedidoIdAndEstado(Long pedidoId, Pago.Estado estado);

	List<Pago> findAllByPedidoIdOrderByIdDesc(Long pedidoId);
}
