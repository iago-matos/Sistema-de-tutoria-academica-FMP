package br.com.tutoria.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.tutoria.model.Mensagem;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    // Historico da conversa em ordem de envio. aposId > 0 traz so as mensagens
    // novas: a tela consulta a cada poucos segundos sem baixar tudo de novo.
    @Query("""
            SELECT m FROM Mensagem m
            JOIN FETCH m.remetente
            WHERE m.conversa.id = :conversaId AND m.id > :aposId
            ORDER BY m.id ASC
            """)
    List<Mensagem> findDaConversa(@Param("conversaId") Long conversaId, @Param("aposId") Long aposId);

    // Mensagens que o leitor ainda nao abriu (as que ele mesmo enviou nao contam)
    @Query("""
            SELECT m FROM Mensagem m
            WHERE m.conversa.id = :conversaId
              AND m.lidaEm IS NULL
              AND m.remetente.id <> :leitorId
            """)
    List<Mensagem> findNaoLidas(@Param("conversaId") Long conversaId, @Param("leitorId") Long leitorId);

    // Nao lidas por conversa, para a lista de conversas: [conversaId, quantidade]
    @Query("""
            SELECT m.conversa.id, COUNT(m) FROM Mensagem m
            WHERE m.conversa.id IN :conversaIds
              AND m.lidaEm IS NULL
              AND m.remetente.id <> :leitorId
            GROUP BY m.conversa.id
            """)
    List<Object[]> contarNaoLidasPorConversa(
            @Param("conversaIds") List<Long> conversaIds, @Param("leitorId") Long leitorId);

    // Total de nao lidas de quem esta logado, em todas as conversas (sino e menu)
    @Query("""
            SELECT COUNT(m) FROM Mensagem m
            WHERE m.lidaEm IS NULL
              AND m.remetente.id <> :usuarioId
              AND (m.conversa.tutor.usuario.id = :usuarioId OR m.conversa.estudante.usuario.id = :usuarioId)
            """)
    long contarNaoLidasDoUsuario(@Param("usuarioId") Long usuarioId);

    Optional<Mensagem> findFirstByConversa_IdOrderByIdDesc(Long conversaId);
}
