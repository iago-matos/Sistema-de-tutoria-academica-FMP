package br.com.tutoria.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.tutoria.model.TutorDisciplina;
import br.com.tutoria.model.enums.TutorDisciplinaStatusEnum;

public interface TutorDisciplinaRepository extends JpaRepository<TutorDisciplina, Long> {

    @Query("""
            SELECT td FROM TutorDisciplina td
            JOIN FETCH td.disciplina d
            WHERE td.tutor.id = :tutorId
            ORDER BY d.nome ASC
            """)
    List<TutorDisciplina> findByTutorId(@Param("tutorId") Long tutorId);

    Optional<TutorDisciplina> findByTutor_IdAndDisciplina_Id(Long tutorId, Long disciplinaId);

    long countByTutor_IdAndStatus(Long tutorId, TutorDisciplinaStatusEnum status);

    // Pedidos de disciplina para a coordenacao, com o nome do tutor na mesma consulta
    @Query("""
            SELECT td FROM TutorDisciplina td
            JOIN FETCH td.disciplina d
            JOIN FETCH td.tutor t
            JOIN FETCH t.usuario u
            WHERE td.status = :status
            ORDER BY td.solicitadaEm ASC
            """)
    List<TutorDisciplina> findByStatusComTutor(@Param("status") TutorDisciplinaStatusEnum status);

    // Disciplinas de varios tutores numa consulta so (lista de tutores, aprovacoes)
    @Query("""
            SELECT td FROM TutorDisciplina td
            JOIN FETCH td.disciplina d
            WHERE td.tutor.id IN :tutorIds AND td.status = :status
            ORDER BY d.nome ASC
            """)
    List<TutorDisciplina> findByTutoresEStatus(
            @Param("tutorIds") Collection<Long> tutorIds,
            @Param("status") TutorDisciplinaStatusEnum status);

    boolean existsByDisciplina_Id(Long disciplinaId);
}
