package cl.farmaexpress.bff.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mantiene el perfil local de cada persona sincronizado con lo que dice su token de Entra ID. */
@Service
public class UsuarioService {

	private final UsuarioRepository usuarios;

	public UsuarioService(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	/**
	 * Busca a la persona por su oid; si no existe, la crea (primer ingreso). Si había una cuenta
	 * antigua con el mismo correo, se asocia a este oid para no perder su carrito ni sus pedidos.
	 */
	@Transactional
	public Usuario sincronizar(UsuarioActual actual) {
		Usuario usuario = usuarios.findByEntraOid(actual.oid())
			.or(() -> actual.email() != null ? usuarios.findByEmail(actual.email()) : java.util.Optional.empty())
			.orElse(null);
		if (usuario == null) {
			try {
				return usuarios.saveAndFlush(Usuario.nuevo(actual));
			}
			catch (DataIntegrityViolationException ex) {
				// Dos peticiones del primer ingreso llegaron a la vez: la otra ya creó el perfil.
				return usuarios.findByEntraOid(actual.oid()).orElseThrow(() -> ex);
			}
		}
		usuario.actualizarDesde(actual);
		return usuario;
	}
}
