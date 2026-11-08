package br.com.tutoria.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.tutoria.model.Disciplina;

public interface DisciplinaRepository extends JpaRepository<Disciplina, Long> {

    List<Disciplina> findAllByOrderByNomeAsc();
}
