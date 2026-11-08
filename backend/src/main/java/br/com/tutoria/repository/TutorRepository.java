package br.com.tutoria.repository;

import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TutorRepository extends JpaRepository<Tutor, Long> {

    Optional<Tutor> findByUsuario_Id(Long usuarioId);

    // JOIN FETCH traz o usuario na mesma consulta (evita uma consulta extra por tutor)
    @Query("""
            SELECT t FROM Tutor t
            JOIN FETCH t.usuario u
            WHERE u.status = :status
            ORDER BY u.nome ASC
            """)
    List<Tutor> findByUsuarioStatus(@Param("status") UsuarioStatusEnum status);

    // IDs dos usuarios que tem perfil de tutor (para listar o tipo de cada usuario)
    @Query("SELECT x.usuario.id FROM Tutor x")
    Set<Long> findTodosUsuarioIds();
}
