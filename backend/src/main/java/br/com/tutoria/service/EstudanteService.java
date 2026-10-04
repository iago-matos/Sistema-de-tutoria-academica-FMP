package br.com.tutoria.service;

import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.UsuarioRepository;
import br.com.tutoria.service.exceptions.DataBindingViolationException;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EstudanteService {

    private final EstudanteRepository estudanteRepository;
    private final UsuarioRepository usuarioRepository;

    public EstudanteService(EstudanteRepository estudanteRepository, UsuarioRepository usuarioRepository) {
        this.estudanteRepository = estudanteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public Estudante salvar(Estudante estudante) {
        if (estudante.getUsuario() == null || estudante.getUsuario().getId() == null) {
            throw new IllegalArgumentException("Informe usuario.id no JSON para vincular o estudante.");
        }
        Usuario usuario = usuarioRepository.findById(estudante.getUsuario().getId())
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Usuario nao encontrado: " + estudante.getUsuario().getId()));
        estudante.setUsuario(usuario);
        return estudanteRepository.save(estudante);
    }

    @Transactional(readOnly = true)
    public Estudante buscarPorId(Long id) {
        return estudanteRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Estudante nao encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<Estudante> listarTodos() {
        return estudanteRepository.findAll();
    }

    @Transactional
    public Estudante atualizar(Long id, Estudante dados) {
        Estudante existente = estudanteRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Estudante nao encontrado: " + id));

        if (dados.getDiaDaSemana() != null) {
            existente.setDiaDaSemana(dados.getDiaDaSemana());
        }
        if (dados.getUsuario() != null && dados.getUsuario().getId() != null) {
            Usuario u = usuarioRepository.findById(dados.getUsuario().getId())
                    .orElseThrow(() -> new ObjectNotFoundException(
                            "Usuario nao encontrado: " + dados.getUsuario().getId()));
            existente.setUsuario(u);
        }
        return estudanteRepository.save(existente);
    }

    @Transactional
    public void deletar(Long id) {
        if (!estudanteRepository.existsById(id)) {
            throw new ObjectNotFoundException("Estudante nao encontrado: " + id);
        }
        try {
            estudanteRepository.deleteById(id);
        } catch (DataIntegrityViolationException ex) {
            throw new DataBindingViolationException(
                    "Nao e possivel excluir pois ha entidades relacionadas");
        }
    }
}
