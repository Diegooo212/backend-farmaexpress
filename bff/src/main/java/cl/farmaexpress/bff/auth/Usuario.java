package cl.farmaexpress.bff.auth;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Perfil de una persona que usa FarmaExpress. La cuenta (correo, contraseña, roles) vive en
 * Microsoft Entra ID; aquí se guarda una copia para asociar carrito y pedidos, y para verla en la base.
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Identificador de la persona en Entra ID (claim "oid"). */
	@Column(name = "entra_oid", unique = true, length = 64)
	private String entraOid;

	@Column(nullable = false, length = 150)
	private String nombre;

	@Column(unique = true, length = 150)
	private String email;

	/** Rol según los App Roles del token (se actualiza en cada ingreso). */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Rol rol;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;

	@Column(name = "ultimo_ingreso")
	private Instant ultimoIngreso;

	protected Usuario() {
	}

	public static Usuario nuevo(UsuarioActual actual) {
		Usuario usuario = new Usuario();
		usuario.creadoEn = Instant.now();
		usuario.actualizarDesde(actual);
		return usuario;
	}

	public void actualizarDesde(UsuarioActual actual) {
		this.entraOid = actual.oid();
		this.nombre = actual.nombre();
		if (actual.email() != null) {
			this.email = actual.email();
		}
		this.rol = actual.rol();
		this.ultimoIngreso = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getEntraOid() {
		return entraOid;
	}

	public String getNombre() {
		return nombre;
	}

	public String getEmail() {
		return email;
	}

	public Rol getRol() {
		return rol;
	}
}
