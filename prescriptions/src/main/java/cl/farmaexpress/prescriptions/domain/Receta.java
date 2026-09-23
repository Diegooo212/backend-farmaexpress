package cl.farmaexpress.prescriptions.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "recetas")
public class Receta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "paciente_nombre", nullable = false, length = 150)
	private String pacienteNombre;

	@Column(nullable = false, length = 12)
	private String rut;

	@Column(nullable = false, length = 150)
	private String email;

	@Column(nullable = false, length = 30)
	private String telefono;

	@Column(name = "tiempo_entrega", nullable = false, length = 20)
	private String tiempoEntrega;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "metodo_despacho", nullable = false, length = 30)
	private MetodoDespacho metodoDespacho;

	@Column(length = 100)
	private String farmacia;

	@Column(length = 255)
	private String direccion;

	@Column(length = 250)
	private String comentarios;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private EstadoReceta status;

	/** oid de Entra ID de la cuenta que envió la receta (viene del JWT, no del formulario). */
	@Column(name = "cuenta_id", length = 64)
	private String cuentaId;

	@Column(name = "cuenta_email", length = 150)
	private String cuentaEmail;

	@Column(name = "cuenta_nombre", length = 150)
	private String cuentaNombre;

	@Column(name = "archivo_nombre", length = 255)
	private String archivoNombre;

	@Column(name = "archivo_tipo", length = 100)
	private String archivoTipo;

	@Column(name = "archivo_tamano")
	private Long archivoTamano;

	@Column(name = "creada_en", nullable = false)
	private Instant creadaEn;

	@OneToMany(mappedBy = "receta", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("fecha ASC, id ASC")
	private List<HistorialEstado> historial = new ArrayList<>();

	protected Receta() {
	}

	public Receta(String pacienteNombre, String rut, String email, String telefono, String tiempoEntrega,
			MetodoDespacho metodoDespacho, String farmacia, String direccion, String comentarios,
			String cuentaId, String cuentaEmail, String cuentaNombre, Instant ahora) {
		this.pacienteNombre = pacienteNombre;
		this.rut = rut;
		this.email = email;
		this.telefono = telefono;
		this.tiempoEntrega = tiempoEntrega;
		this.metodoDespacho = metodoDespacho;
		this.farmacia = farmacia;
		this.direccion = direccion;
		this.comentarios = comentarios;
		this.cuentaId = cuentaId;
		this.cuentaEmail = cuentaEmail;
		this.cuentaNombre = cuentaNombre;
		this.creadaEn = ahora;
		this.status = EstadoReceta.INGRESADA;
		this.historial.add(new HistorialEstado(this, EstadoReceta.INGRESADA, ahora, null, cuentaNombre));
	}

	public void adjuntarArchivo(String nombre, String tipo, long tamano) {
		this.archivoNombre = nombre;
		this.archivoTipo = tipo;
		this.archivoTamano = tamano;
	}

	/** Cambia el estado dejando registro. Quien llama debe validar antes con {@link EstadoReceta#puedePasarA}. */
	public void cambiarEstado(EstadoReceta nuevo, String nota, String por, Instant ahora) {
		if (!status.puedePasarA(nuevo)) {
			throw new IllegalStateException("Transición no permitida: " + status + " → " + nuevo);
		}
		this.status = nuevo;
		this.historial.add(new HistorialEstado(this, nuevo, ahora, nota, por));
	}

	public boolean perteneceA(String cuentaId) {
		return this.cuentaId != null && this.cuentaId.equals(cuentaId);
	}

	public Long getId() {
		return id;
	}

	public String getPacienteNombre() {
		return pacienteNombre;
	}

	public String getRut() {
		return rut;
	}

	public String getEmail() {
		return email;
	}

	public String getTelefono() {
		return telefono;
	}

	public String getTiempoEntrega() {
		return tiempoEntrega;
	}

	public MetodoDespacho getMetodoDespacho() {
		return metodoDespacho;
	}

	public String getFarmacia() {
		return farmacia;
	}

	public String getDireccion() {
		return direccion;
	}

	public String getComentarios() {
		return comentarios;
	}

	public EstadoReceta getStatus() {
		return status;
	}

	public String getCuentaId() {
		return cuentaId;
	}

	public String getCuentaEmail() {
		return cuentaEmail;
	}

	public String getCuentaNombre() {
		return cuentaNombre;
	}

	public String getArchivoNombre() {
		return archivoNombre;
	}

	public String getArchivoTipo() {
		return archivoTipo;
	}

	public Long getArchivoTamano() {
		return archivoTamano;
	}

	public Instant getCreadaEn() {
		return creadaEn;
	}

	public List<HistorialEstado> getHistorial() {
		return historial;
	}
}
