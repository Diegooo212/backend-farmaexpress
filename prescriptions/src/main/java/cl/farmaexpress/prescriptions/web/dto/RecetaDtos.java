package cl.farmaexpress.prescriptions.web.dto;

import java.time.ZoneId;
import java.util.List;

import cl.farmaexpress.prescriptions.domain.EstadoReceta;
import cl.farmaexpress.prescriptions.domain.HistorialEstado;
import cl.farmaexpress.prescriptions.domain.MetodoDespacho;
import cl.farmaexpress.prescriptions.domain.Receta;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos JSON de recetas. Los nombres de campo coinciden con los que usa el frontend. */
public final class RecetaDtos {

	private RecetaDtos() {
	}

	/** Datos del formulario "Enviar una receta". La cuenta que la envía se toma del JWT, no de aquí. */
	public record RecetaRequest(
			@NotBlank(message = "Escribe el nombre del paciente.")
			@Size(max = 150, message = "El nombre admite hasta 150 caracteres.") String pacienteNombre,
			@NotBlank(message = "Escribe el RUT del paciente.")
			@Size(max = 12, message = "El RUT admite hasta 12 caracteres.") String rut,
			@NotBlank(message = "Escribe un correo de contacto.")
			@Email(message = "El correo de contacto no es válido.")
			@Size(max = 150, message = "El correo admite hasta 150 caracteres.") String email,
			@NotBlank(message = "Escribe un teléfono de contacto.")
			@Size(max = 30, message = "El teléfono admite hasta 30 caracteres.") String telefono,
			@Size(max = 20) String tiempoEntrega,
			@NotNull(message = "Elige retiro en farmacia o despacho a domicilio.") MetodoDespacho metodoDespacho,
			@Size(max = 100) String farmacia,
			@Size(max = 255, message = "La dirección admite hasta 255 caracteres.") String direccion,
			@Size(max = 250, message = "Los comentarios admiten hasta 250 caracteres.") String comentarios) {
	}

	public record CambioEstadoRequest(
			@NotNull(message = "Indica el nuevo estado.") EstadoReceta status,
			@Size(max = 300, message = "El mensaje para el paciente admite hasta 300 caracteres.") String nota) {
	}

	public record HistorialResponse(EstadoReceta status, String fecha, String nota, String por) {

		static HistorialResponse from(HistorialEstado h) {
			return new HistorialResponse(h.getStatus(), h.getFecha().toString(), h.getNota(), h.getPor());
		}
	}

	public record RecetaResponse(
			Long id,
			String pacienteNombre,
			String rut,
			String email,
			String telefono,
			String tiempoEntrega,
			MetodoDespacho metodoDespacho,
			String farmacia,
			String direccion,
			String comentarios,
			EstadoReceta status,
			/** Fecha de envío (AAAA-MM-DD) en la hora local de la farmacia. */
			String fechaCreacion,
			String creadaEn,
			String cuentaId,
			String cuentaEmail,
			String cuentaNombre,
			String archivoNombre,
			String archivoTipo,
			Long archivoTamano,
			boolean tieneArchivo,
			List<HistorialResponse> historial) {

		public static RecetaResponse from(Receta r, ZoneId zona) {
			return new RecetaResponse(
					r.getId(),
					r.getPacienteNombre(),
					r.getRut(),
					r.getEmail(),
					r.getTelefono(),
					r.getTiempoEntrega(),
					r.getMetodoDespacho(),
					r.getFarmacia(),
					r.getDireccion(),
					r.getComentarios(),
					r.getStatus(),
					r.getCreadaEn().atZone(zona).toLocalDate().toString(),
					r.getCreadaEn().toString(),
					r.getCuentaId(),
					r.getCuentaEmail(),
					r.getCuentaNombre(),
					r.getArchivoNombre(),
					r.getArchivoTipo(),
					r.getArchivoTamano(),
					r.getArchivoNombre() != null,
					r.getHistorial().stream().map(HistorialResponse::from).toList());
		}
	}
}
