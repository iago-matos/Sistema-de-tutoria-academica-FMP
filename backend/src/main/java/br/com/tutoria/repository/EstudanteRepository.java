package br.com.tutoria.repository;

import br.com.tutoria.model.Estudante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface EstudanteRepository extends JpaRepository<Estudante, Long> {

    Optional<Estudante> findByUsuario_Id(Long usuarioId);

    // IDs dos usuarios que tem perfil de estudante (para listar o tipo de cada usuario)
    @Query("SELECT x.usuario.id FROM Estudante x")
    Set<Long> findTodosUsuarioIds();
}
