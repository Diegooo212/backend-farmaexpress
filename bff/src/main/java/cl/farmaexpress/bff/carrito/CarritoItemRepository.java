package cl.farmaexpress.bff.carrito;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarritoItemRepository extends JpaRepository<CarritoItem, Long> {

	List<CarritoItem> findAllByUsuarioIdOrderByIdAsc(Long usuarioId);

	/** Borrado directo en la base (antes de insertar el carrito nuevo, para no chocar con la clave única). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from CarritoItem c where c.usuarioId = :usuarioId")
	void vaciar(@Param("usuarioId") Long usuarioId);
}
