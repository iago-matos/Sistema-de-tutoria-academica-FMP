package br.com.tutoria.repository;

import br.com.tutoria.dto.AvaliacaoEstatisticaDTO;
import br.com.tutoria.model.Avaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    List<Avaliacao> findByTutor_Id(Long tutorId);

    List<Avaliacao> findByEstudante_Id(Long estudanteId);

    // Media e total por tutor numa unica consulta, sem enviar as avaliacoes ao cliente
    @Query("""
            SELECT new br.com.tutoria.dto.AvaliacaoEstatisticaDTO(a.tutor.id, AVG(a.nota), COUNT(a))
            FROM Avaliacao a
            GROUP BY a.tutor.id
            """)
    List<AvaliacaoEstatisticaDTO> calcularEstatisticasPorTutor();
}
