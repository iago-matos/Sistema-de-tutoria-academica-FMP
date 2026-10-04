package br.com.tutoria.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.model.Coordenador;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.ProfileEnum;
import br.com.tutoria.repository.CoordenadorRepository;
import br.com.tutoria.repository.UsuarioRepository;
import br.com.tutoria.service.exceptions.DataBindingViolationException;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;

@Service
public class CoordenadorService {

    private final CoordenadorRepository coordenadorRepository;
    private final UsuarioRepository usuarioRepository;

    public CoordenadorService(CoordenadorRepository coordenadorRepository, UsuarioRepository usuarioRepository) {
        this.coordenadorRepository = coordenadorRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public Coordenador salvar(Coordenador coordenador) {
        if (coordenador.getUsuario() == null || coordenador.getUsuario().getId() == null) {
            throw new IllegalArgumentException("Informe usuario.id no JSON para vincular o coordenador.");
        }
        Usuario usuario = usuarioRepository.findById(coordenador.getUsuario().getId())
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Usuario nao encontrado: " + coordenador.getUsuario().getId()));

        // Promove o usuário para ROLE_ADMIN ao torná-lo coordenador
        usuario.getProfiles().add(ProfileEnum.ADMIN);
        usuarioRepository.save(usuario);

        coordenador.setUsuario(usuario);
        return coordenadorRepository.save(coordenador);
    }

    @Transactional(readOnly = true)
    public Coordenador buscarPorId(Long id) {
        return coordenadorRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Coordenador nao encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<Coordenador> listarTodos() {
        return coordenadorRepository.findAll();
    }

    @Transactional
    public Coordenador atualizar(Long id, Coordenador dados) {
        Coordenador existente = coordenadorRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Coordenador nao encontrado: " + id));

        if (dados.getRelatorio() != null) {
            existente.setRelatorio(dados.getRelatorio());
        }
        if (dados.getUsuario() != null && dados.getUsuario().getId() != null) {
            Usuario u = usuarioRepository.findById(dados.getUsuario().getId())
                    .orElseThrow(() -> new ObjectNotFoundException(
                            "Usuario nao encontrado: " + dados.getUsuario().getId()));
            existente.setUsuario(u);
        }
        return coordenadorRepository.save(existente);
    }

    @Transactional
    public void deletar(Long id) {
        if (!coordenadorRepository.existsById(id)) {
            throw new ObjectNotFoundException("Coordenador nao encontrado: " + id);
        }
        try {
            coordenadorRepository.deleteById(id);
        } catch (DataIntegrityViolationException ex) {
            throw new DataBindingViolationException(
                    "Nao e possivel excluir pois ha entidades relacionadas");
        }
    }
}
