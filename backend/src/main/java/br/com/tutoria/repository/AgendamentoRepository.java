package br.com.tutoria.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.enums.AgendamentoStatusEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import jakarta.persistence.LockModeType;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    // JOIN FETCH garante que tutor.usuario e estudante.usuario são carregados
    // numa única query, evitando LazyInitializationException e o nome "--" no front

    @Query("""
            SELECT a FROM Agendamento a
            LEFT JOIN FETCH a.tutor t
            LEFT JOIN FETCH t.usuario
            LEFT JOIN FETCH a.estudante e
            LEFT JOIN FETCH e.usuario
            WHERE a.id = :id
            """)
    Optional<Agendamento> findByIdFull(@Param("id") Long id);

    // Trava a linha ate o fim da transacao: se dois estudantes clicarem em
    // "Solicitar" no mesmo horario ao mesmo tempo, o segundo espera o primeiro
    // terminar e entao recebe "horario nao esta mais disponivel".
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Agendamento a WHERE a.id = :id")
    Optional<Agendamento> findByIdParaAlterar(@Param("id") Long id);

    @Query("""
            SELECT a FROM Agendamento a
            LEFT JOIN FETCH a.tutor t
            LEFT JOIN FETCH t.usuario
            LEFT JOIN FETCH a.estudante e
            LEFT JOIN FETCH e.usuario
            WHERE t.id = :tutorId
            ORDER BY a.data ASC, a.hora ASC
            """)
    List<Agendamento> findByTutor_IdOrderByDataAscHoraAsc(@Param("tutorId") Long tutorId);

    @Query("""
            SELECT a FROM Agendamento a
            LEFT JOIN FETCH a.tutor t
            LEFT JOIN FETCH t.usuario
            LEFT JOIN FETCH a.estudante e
            LEFT JOIN FETCH e.usuario
            WHERE e.id = :estudanteId
            ORDER BY a.data ASC, a.hora ASC
            """)
    List<Agendamento> findByEstudante_IdOrderByDataAscHoraAsc(@Param("estudanteId") Long estudanteId);

    // Horarios livres a partir de uma data, apenas de tutores com conta ativa
    @Query("""
            SELECT a FROM Agendamento a
            JOIN FETCH a.tutor t
            JOIN FETCH t.usuario u
            WHERE a.status = br.com.tutoria.model.enums.AgendamentoStatusEnum.DISPONIVEL
              AND u.status = :statusTutor
              AND a.data >= :aPartirDe
            ORDER BY a.data ASC, a.hora ASC
            """)
    List<Agendamento> findDisponiveis(
            @Param("aPartirDe") LocalDate aPartirDe,
            @Param("statusTutor") UsuarioStatusEnum statusTutor);

    // Mesma consulta filtrando pela disciplina, sem diferenciar maiusculas.
    // O horario livre aparece em todas as disciplinas ATIVAS do tutor.
    @Query("""
            SELECT a FROM Agendamento a
            JOIN FETCH a.tutor t
            JOIN FETCH t.usuario u
            WHERE a.status = br.com.tutoria.model.enums.AgendamentoStatusEnum.DISPONIVEL
              AND u.status = :statusTutor
              AND a.data >= :aPartirDe
              AND EXISTS (
                  SELECT 1 FROM TutorDisciplina td
                  WHERE td.tutor = t
                    AND td.status = br.com.tutoria.model.enums.TutorDisciplinaStatusEnum.ATIVA
                    AND LOWER(td.disciplina.nome) = LOWER(TRIM(:disciplina)))
            ORDER BY a.data ASC, a.hora ASC
            """)
    List<Agendamento> findDisponiveisPorDisciplina(
            @Param("aPartirDe") LocalDate aPartirDe,
            @Param("statusTutor") UsuarioStatusEnum statusTutor,
            @Param("disciplina") String disciplina);

    @Query("""
            SELECT a FROM Agendamento a
            JOIN FETCH a.tutor t
            JOIN FETCH t.usuario u
            WHERE a.status = br.com.tutoria.model.enums.AgendamentoStatusEnum.DISPONIVEL
              AND u.status = :statusTutor
              AND a.data >= :aPartirDe
              AND t.id = :tutorId
            ORDER BY a.data ASC, a.hora ASC
            """)
    List<Agendamento> findDisponiveisPorTutor(
            @Param("aPartirDe") LocalDate aPartirDe,
            @Param("statusTutor") UsuarioStatusEnum statusTutor,
            @Param("tutorId") Long tutorId);

    // Usadas para detectar conflito de horario no mesmo dia
    List<Agendamento> findByTutor_IdAndData(Long tutorId, LocalDate data);

    List<Agendamento> findByEstudante_IdAndDataAndStatusIn(
            Long estudanteId, LocalDate data, Collection<AgendamentoStatusEnum> status);

    // A coordenacao so exclui uma disciplina que nenhuma tutoria registrou
    boolean existsByDisciplina_Id(Long disciplinaId);

    // Usadas na inativacao de um tutor ou estudante (InativacaoDeContaService)
    List<Agendamento> findByTutor_Id(Long tutorId);

    List<Agendamento> findByEstudante_Id(Long estudanteId);

    @Query("""
            SELECT a FROM Agendamento a
            LEFT JOIN FETCH a.tutor t
            LEFT JOIN FETCH t.usuario
            LEFT JOIN FETCH a.estudante e
            LEFT JOIN FETCH e.usuario
            ORDER BY a.data ASC, a.hora ASC
            """)
    List<Agendamento> findAllFull();
}
