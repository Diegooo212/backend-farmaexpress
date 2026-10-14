package cl.farmaexpress.notifications.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

	boolean existsByEventoId(String eventoId);

	List<Notificacion> findTop30ByCuentaIdOrderByFechaDescIdDesc(String cuentaId);

	List<Notificacion> findAllByCuentaIdAndLeidaFalse(String cuentaId);
}
