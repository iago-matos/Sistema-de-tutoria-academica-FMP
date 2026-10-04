package br.com.tutoria.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.tutoria.model.Agendamento;

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

    @Query("""
            SELECT a FROM Agendamento a
            LEFT JOIN FETCH a.tutor t
            LEFT JOIN FETCH t.usuario
            WHERE a.status = :status
            ORDER BY a.data ASC, a.hora ASC
            """)
    List<Agendamento> findByStatusOrderByDataAscHoraAsc(@Param("status") String status);

    @Query("""
            SELECT a FROM Agendamento a
            LEFT JOIN FETCH a.tutor t
            LEFT JOIN FETCH t.usuario
            WHERE t.id = :tutorId AND a.status = :status
            ORDER BY a.data ASC, a.hora ASC
            """)
    List<Agendamento> findByTutor_IdAndStatusOrderByDataAscHoraAsc(
            @Param("tutorId") Long tutorId,
            @Param("status") String status);

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
