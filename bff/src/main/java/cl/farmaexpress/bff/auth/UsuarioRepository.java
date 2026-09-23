package cl.farmaexpress.bff.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEntraOid(String entraOid);

	Optional<Usuario> findByEmail(String email);
}
