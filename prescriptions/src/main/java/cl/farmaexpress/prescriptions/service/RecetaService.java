package cl.farmaexpress.prescriptions.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import cl.farmaexpress.prescriptions.domain.EstadoReceta;
import cl.farmaexpress.prescriptions.domain.MetodoDespacho;
import cl.farmaexpress.prescriptions.domain.Receta;
import cl.farmaexpress.prescriptions.domain.RecetaArchivo;
import cl.farmaexpress.prescriptions.domain.RecetaArchivoRepository;
import cl.farmaexpress.prescriptions.domain.RecetaRepository;
import cl.farmaexpress.prescriptions.web.dto.RecetaDtos.CambioEstadoRequest;
import cl.farmaexpress.prescriptions.web.dto.RecetaDtos.RecetaRequest;
import cl.farmaexpress.prescriptions.web.dto.RecetaDtos.RecetaResponse;

@Service
public class RecetaService {

	private final RecetaRepository recetas;
	private final RecetaArchivoRepository archivos;
	private final ZoneId zona;
	private final Clock clock;

	public RecetaService(RecetaRepository recetas, RecetaArchivoRepository archivos,
			@Value("${farmaexpress.zona-horaria}") String zona) {
		this.recetas = recetas;
		this.archivos = archivos;
		this.zona = ZoneId.of(zona);
		this.clock = Clock.systemUTC();
	}

	/** El personal de farmacia ve todas las recetas; un paciente, solo las que envió su cuenta. */
	@Transactional(readOnly = true)
	public List<RecetaResponse> listar(Usuario usuario, EstadoReceta status) {
		List<Receta> resultado;
		if (usuario.esPersonalFarmacia()) {
			resultado = status == null ? recetas.findAllByOrderByCreadaEnDescIdDesc()
					: recetas.findAllByStatusOrderByCreadaEnDescIdDesc(status);
		}
		else {
			resultado = status == null ? recetas.findAllByCuentaIdOrderByCreadaEnDescIdDesc(usuario.id())
					: recetas.findAllByCuentaIdAndStatusOrderByCreadaEnDescIdDesc(usuario.id(), status);
		}
		return resultado.stream().map(this::aRespuesta).toList();
	}

	@Transactional(readOnly = true)
	public RecetaResponse obtener(Long id, Usuario usuario) {
		return aRespuesta(buscarVisible(id, usuario));
	}

	@Transactional
	public RecetaResponse crear(RecetaRequest datos, MultipartFile archivoSubido, Usuario usuario) {
		if (!Rut.esValido(datos.rut())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa el RUT: el dígito verificador no coincide.");
		}
		boolean retiro = datos.metodoDespacho() == MetodoDespacho.RETIRO_TIENDA;
		if (retiro && vacio(datos.farmacia())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige la farmacia donde retirarás la receta.");
		}
		if (!retiro && vacio(datos.direccion())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe la dirección donde quieres recibir la receta.");
		}
		ArchivoReceta archivo = ArchivoReceta.validar(archivoSubido);

		Receta receta = new Receta(
				datos.pacienteNombre().trim(),
				datos.rut().trim(),
				datos.email().trim(),
				datos.telefono().trim(),
				vacio(datos.tiempoEntrega()) ? "NORMAL" : datos.tiempoEntrega().trim(),
				datos.metodoDespacho(),
				retiro ? datos.farmacia().trim() : null,
				retiro ? null : datos.direccion().trim(),
				vacio(datos.comentarios()) ? null : datos.comentarios().trim(),
				usuario.id(),
				usuario.email(),
				usuario.nombre(),
				Instant.now(clock));
		receta.adjuntarArchivo(archivo.nombre(), archivo.tipo(), archivo.contenido().length);
		receta = recetas.save(receta);
		archivos.save(new RecetaArchivo(receta.getId(), archivo.contenido()));
		return aRespuesta(receta);
	}

	@Transactional
	public RecetaResponse cambiarEstado(Long id, CambioEstadoRequest cambio, Usuario usuario) {
		if (!usuario.esPersonalFarmacia()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el personal de farmacia puede cambiar el estado.");
		}
		Receta receta = recetas.findWithHistorialById(id).orElseThrow(() -> noEncontrada(id));
		EstadoReceta actual = receta.getStatus();
		EstadoReceta nuevo = cambio.status();

		if (!actual.puedePasarA(nuevo)) {
			String detalle = nuevo == EstadoReceta.RECHAZADA
					? "Una receta en estado " + actual + " ya no se puede rechazar."
					: "Una receta en estado " + actual + " no puede pasar a " + nuevo + ".";
			throw new ResponseStatusException(HttpStatus.CONFLICT, detalle);
		}
		String nota = vacio(cambio.nota()) ? null : cambio.nota().trim();
		if (nuevo == EstadoReceta.RECHAZADA && nota == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Escribe el motivo del rechazo: el paciente lo verá para saber qué corregir.");
		}

		receta.cambiarEstado(nuevo, nota, usuario.nombre(), Instant.now(clock));
		recetas.flush();
		return aRespuesta(receta);
	}

	@Transactional(readOnly = true)
	public ArchivoDescarga archivo(Long id, Usuario usuario) {
		Receta receta = buscarVisible(id, usuario);
		RecetaArchivo archivo = archivos.findById(receta.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Esta receta no tiene archivo adjunto."));
		return new ArchivoDescarga(receta.getArchivoNombre(), receta.getArchivoTipo(), archivo.getContenido());
	}

	public record ArchivoDescarga(String nombre, String tipo, byte[] contenido) {
	}

	/** Si la receta es de otra cuenta se responde 404, sin revelar que existe. */
	private Receta buscarVisible(Long id, Usuario usuario) {
		Receta receta = recetas.findWithHistorialById(id).orElseThrow(() -> noEncontrada(id));
		if (!usuario.esPersonalFarmacia() && !receta.perteneceA(usuario.id())) {
			throw noEncontrada(id);
		}
		return receta;
	}

	private RecetaResponse aRespuesta(Receta receta) {
		return RecetaResponse.from(receta, zona);
	}

	private static ResponseStatusException noEncontrada(Long id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la receta " + id + ".");
	}

	private static boolean vacio(String valor) {
		return valor == null || valor.isBlank();
	}
}
