package cl.farmaexpress.bff.auth;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Mantiene el perfil local de cada persona sincronizado con lo que dice su token de Entra ID. */
@Service
public class UsuarioService {

	private final UsuarioRepository usuarios;
	private final TransactionTemplate transaccionPropia;

	public UsuarioService(UsuarioRepository usuarios, PlatformTransactionManager transacciones) {
		this.usuarios = usuarios;
		this.transaccionPropia = new TransactionTemplate(transacciones);
		this.transaccionPropia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * Busca a la persona por su oid; si no existe, la crea (primer ingreso). Si había una cuenta
	 * antigua con el mismo correo, se asocia a este oid para no perder su carrito ni sus pedidos.
	 * <p>
	 * Al iniciar sesión el frontend hace varias peticiones a la vez (perfil, carrito, pedidos) y todas
	 * pueden intentar crear el mismo perfil. Solo una lo logra; las demás chocan con la clave única y
	 * se reintenta en una transacción nueva, donde el perfil ya existe. (Reintentar dentro de la misma
	 * transacción no sirve: Hibernate deja la sesión inutilizable después del error.)
	 */
	public Usuario sincronizar(UsuarioActual actual) {
		try {
			return transaccionPropia.execute(estado -> buscarOCrear(actual));
		}
		catch (DataIntegrityViolationException ex) {
			return transaccionPropia.execute(estado -> buscarOCrear(actual));
		}
	}

	private Usuario buscarOCrear(UsuarioActual actual) {
		Usuario usuario = usuarios.findByEntraOid(actual.oid())
			.or(() -> actual.email() != null ? usuarios.findByEmail(actual.email()) : Optional.empty())
			.orElse(null);
		if (usuario == null) {
			return usuarios.saveAndFlush(Usuario.nuevo(actual));
		}
		usuario.actualizarDesde(actual);
		return usuario;
	}
}
