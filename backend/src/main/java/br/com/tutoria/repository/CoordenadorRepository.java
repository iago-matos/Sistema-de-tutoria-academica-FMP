package br.com.tutoria.repository;

import br.com.tutoria.model.Coordenador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CoordenadorRepository extends JpaRepository<Coordenador, Long> {

    Optional<Coordenador> findByUsuario_Id(Long usuarioId);
}
