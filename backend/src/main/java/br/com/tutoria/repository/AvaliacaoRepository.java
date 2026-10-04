package br.com.tutoria.repository;

import br.com.tutoria.model.Avaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    List<Avaliacao> findByTutor_Id(Long tutorId);

    List<Avaliacao> findByEstudante_Id(Long estudanteId);
}

