package cl.farmaexpress.bff.cuenta;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import cl.farmaexpress.bff.auth.Usuario;
import cl.farmaexpress.bff.auth.UsuarioRepository;

/** Mi cuenta: datos de contacto, direcciones guardadas para el despacho y favoritos. */
@Service
public class CuentaService {

	/** Como en las tiendas: unas pocas direcciones frecuentes (casa, trabajo, familia). */
	static final int MAX_DIRECCIONES = 5;

	static final int MAX_FAVORITOS = 100;

	private final UsuarioRepository usuarios;
	private final DireccionRepository direcciones;
	private final FavoritoRepository favoritos;

	public CuentaService(UsuarioRepository usuarios, DireccionRepository direcciones, FavoritoRepository favoritos) {
		this.usuarios = usuarios;
		this.direcciones = direcciones;
		this.favoritos = favoritos;
	}

	/** Nombre y correo vienen de la cuenta de Microsoft; el teléfono lo edita la persona aquí. */
	public record PerfilResponse(String nombre, String email, String rol, String telefono, String miembroDesde) {
	}

	public record PerfilRequest(
			@Size(max = 20, message = "El teléfono es muy largo.")
			@Pattern(regexp = "^$|^\\+?[0-9 ]{8,20}$", message = "Escribe el teléfono solo con números, por ejemplo +56 9 1234 5678.") String telefono) {
	}

	public record DireccionRequest(
			@NotBlank(message = "Ponle un nombre a la dirección (por ejemplo, Casa).") @Size(max = 40) String alias,
			@NotBlank(message = "Escribe la calle y el número.") @Size(max = 120) String calle,
			@Size(max = 30) String depto,
			@NotBlank(message = "Escribe la comuna.") @Size(max = 40) String comuna,
			Boolean predeterminada) {
	}

	public record DireccionResponse(Long id, String alias, String calle, String depto, String comuna,
			boolean predeterminada, String completa) {

		static DireccionResponse from(Direccion d) {
			return new DireccionResponse(d.getId(), d.getAlias(), d.getCalle(), d.getDepto(), d.getComuna(),
					d.isPredeterminada(), d.completa());
		}
	}

	@Transactional(readOnly = true)
	public PerfilResponse perfil(Long usuarioId) {
		Usuario u = usuarios.findById(usuarioId).orElseThrow();
		return new PerfilResponse(u.getNombre(), u.getEmail(), u.getRol().name(), u.getTelefono(),
				u.getCreadoEn().toString());
	}

	@Transactional
	public PerfilResponse actualizarPerfil(Long usuarioId, PerfilRequest request) {
		usuarios.findById(usuarioId).orElseThrow().cambiarTelefono(request.telefono());
		return perfil(usuarioId);
	}

	@Transactional(readOnly = true)
	public List<DireccionResponse> direcciones(Long usuarioId) {
		return direcciones.findAllByUsuarioIdOrderByPredeterminadaDescIdAsc(usuarioId).stream()
			.map(DireccionResponse::from)
			.toList();
	}

	@Transactional
	public DireccionResponse agregar(Long usuarioId, DireccionRequest request) {
		long cantidad = direcciones.countByUsuarioId(usuarioId);
		if (cantidad >= MAX_DIRECCIONES) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Puedes guardar hasta " + MAX_DIRECCIONES + " direcciones. Elimina una para agregar otra.");
		}
		Direccion d = new Direccion(usuarioId);
		d.actualizar(request.alias(), request.calle(), request.depto(), request.comuna());
		// La primera dirección queda como predeterminada.
		boolean predeterminada = cantidad == 0 || Boolean.TRUE.equals(request.predeterminada());
		if (predeterminada) {
			quitarPredeterminada(usuarioId);
		}
		d.marcarPredeterminada(predeterminada);
		return DireccionResponse.from(direcciones.save(d));
	}

	@Transactional
	public DireccionResponse actualizar(Long usuarioId, Long id, DireccionRequest request) {
		Direccion d = propia(usuarioId, id);
		d.actualizar(request.alias(), request.calle(), request.depto(), request.comuna());
		if (Boolean.TRUE.equals(request.predeterminada()) && !d.isPredeterminada()) {
			quitarPredeterminada(usuarioId);
			d.marcarPredeterminada(true);
		}
		return DireccionResponse.from(d);
	}

	@Transactional
	public void eliminar(Long usuarioId, Long id) {
		Direccion d = propia(usuarioId, id);
		boolean eraPredeterminada = d.isPredeterminada();
		direcciones.delete(d);
		direcciones.flush();
		if (eraPredeterminada) {
			// Otra dirección pasa a ser la predeterminada.
			direcciones.findAllByUsuarioIdOrderByPredeterminadaDescIdAsc(usuarioId).stream()
				.findFirst()
				.ifPresent(otra -> otra.marcarPredeterminada(true));
		}
	}

	/** Ids de los productos favoritos, del más reciente al más antiguo. */
	@Transactional(readOnly = true)
	public List<Long> favoritos(Long usuarioId) {
		return favoritos.findAllByUsuarioIdOrderByCreadoEnDesc(usuarioId).stream()
			.map(Favorito::getMedicamentoId)
			.toList();
	}

	/** Idempotente: marcar dos veces el mismo producto no lo duplica. */
	@Transactional
	public List<Long> agregarFavorito(Long usuarioId, Long medicamentoId) {
		if (!favoritos.existsById(new Favorito.Clave(usuarioId, medicamentoId))) {
			if (favoritos.countByUsuarioId(usuarioId) >= MAX_FAVORITOS) {
				throw new ResponseStatusException(HttpStatus.CONFLICT,
						"Puedes tener hasta " + MAX_FAVORITOS + " favoritos.");
			}
			favoritos.save(new Favorito(usuarioId, medicamentoId));
		}
		return favoritos(usuarioId);
	}

	@Transactional
	public List<Long> quitarFavorito(Long usuarioId, Long medicamentoId) {
		favoritos.deleteById(new Favorito.Clave(usuarioId, medicamentoId));
		return favoritos(usuarioId);
	}

	private void quitarPredeterminada(Long usuarioId) {
		direcciones.findAllByUsuarioIdOrderByPredeterminadaDescIdAsc(usuarioId)
			.forEach(d -> d.marcarPredeterminada(false));
	}

	/** Una dirección de otra cuenta responde 404 (no se revela que existe). */
	private Direccion propia(Long usuarioId, Long id) {
		return direcciones.findById(id)
			.filter(d -> d.getUsuarioId().equals(usuarioId))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa dirección."));
	}
}
