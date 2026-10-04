package br.com.tutoria.repository;

import br.com.tutoria.model.Estudante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstudanteRepository extends JpaRepository<Estudante, Long> {

    Optional<Estudante> findByUsuario_Id(Long usuarioId);
}
