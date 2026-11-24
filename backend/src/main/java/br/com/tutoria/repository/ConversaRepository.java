package br.com.tutoria.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.tutoria.model.Conversa;

public interface ConversaRepository extends JpaRepository<Conversa, Long> {

    // JOIN FETCH traz tutor, estudante e os usuarios numa consulta so:
    // a tela precisa do nome e do status de quem esta do outro lado

    @Query("""
            SELECT c FROM Conversa c
            JOIN FETCH c.tutor t
            JOIN FETCH t.usuario
            JOIN FETCH c.estudante e
            JOIN FETCH e.usuario
            WHERE c.id = :id
            """)
    Optional<Conversa> findByIdFull(@Param("id") Long id);

    Optional<Conversa> findByTutor_IdAndEstudante_Id(Long tutorId, Long estudanteId);

    // Conversas de quem esta logado (como tutor ou como estudante), a mais recente primeiro
    @Query("""
            SELECT c FROM Conversa c
            JOIN FETCH c.tutor t
            JOIN FETCH t.usuario ut
            JOIN FETCH c.estudante e
            JOIN FETCH e.usuario ue
            WHERE ut.id = :usuarioId OR ue.id = :usuarioId
            ORDER BY COALESCE(c.ultimaMensagemEm, c.criadaEm) DESC
            """)
    List<Conversa> findDoUsuario(@Param("usuarioId") Long usuarioId);
}
