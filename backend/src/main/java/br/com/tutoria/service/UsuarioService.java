package br.com.tutoria.service;
import br.com.tutoria.dto.UsuarioMeDTO;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.ProfileEnum;
import br.com.tutoria.model.enums.UsuarioTipoEnum;
import br.com.tutoria.repository.CoordenadorRepository;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.repository.UsuarioRepository;
import br.com.tutoria.service.exceptions.DataBindingViolationException;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EstudanteRepository estudanteRepository;
    private final TutorRepository tutorRepository;
    private final CoordenadorRepository coordenadorRepository;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            EstudanteRepository estudanteRepository,
            TutorRepository tutorRepository,
            CoordenadorRepository coordenadorRepository) {

        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.estudanteRepository = estudanteRepository;
        this.tutorRepository = tutorRepository;
        this.coordenadorRepository = coordenadorRepository;
    }

    @Transactional
    public Usuario salvar(Usuario usuario) {

        usuarioRepository.findByEmail(usuario.getEmail()).ifPresent(u -> {
            throw new IllegalArgumentException(
                    "Email ja cadastrado: " + usuario.getEmail());
        });

        // Criptografa senha
        usuario.setSenha(
                passwordEncoder.encode(usuario.getSenha()));

        // Adiciona perfil padrão
        usuario.getProfiles().add(ProfileEnum.USER);

        return usuarioRepository.save(usuario);
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorId(Long id) {

        return usuarioRepository.findById(id)
                .orElseThrow(() ->
                        new ObjectNotFoundException(
                                "Usuario nao encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    @Transactional(readOnly = true)
    public UsuarioMeDTO buscarMe(Long usuarioId) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() ->
                        new ObjectNotFoundException(
                                "Usuario nao encontrado: " + usuarioId));

        UsuarioTipoEnum tipo = resolverTipo(usuario);

        return new UsuarioMeDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                tipo);
    }

    private UsuarioTipoEnum resolverTipo(Usuario usuario) {

        if (usuario.getProfiles().contains(ProfileEnum.ADMIN)
                || coordenadorRepository.findByUsuario_Id(usuario.getId()).isPresent()) {
            return UsuarioTipoEnum.ADMIN;
        }

        if (tutorRepository.findByUsuario_Id(usuario.getId()).isPresent()) {
            return UsuarioTipoEnum.TUTOR;
        }

        if (estudanteRepository.findByUsuario_Id(usuario.getId()).isPresent()) {
            return UsuarioTipoEnum.ESTUDANTE;
        }

        return UsuarioTipoEnum.ESTUDANTE;
    }

    @Transactional
    public Usuario atualizar(Long id, Usuario dados) {

        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() ->
                        new ObjectNotFoundException(
                                "Usuario nao encontrado: " + id));

        if (dados.getEmail() != null
                && !dados.getEmail().equals(existente.getEmail())) {

            usuarioRepository.findByEmail(dados.getEmail()).ifPresent(u -> {
                throw new IllegalArgumentException(
                        "Email ja cadastrado: " + dados.getEmail());
            });
        }

        if (dados.getNome() != null) {
            existente.setNome(dados.getNome());
        }

        if (dados.getEmail() != null) {
            existente.setEmail(dados.getEmail());
        }

        if (dados.getSenha() != null) {

            // Atualiza senha criptografada
            existente.setSenha(
                    passwordEncoder.encode(dados.getSenha()));
        }

        return usuarioRepository.save(existente);
    }

    @Transactional
    public void deletar(Long id) {

        if (!usuarioRepository.existsById(id)) {

            throw new ObjectNotFoundException(
                    "Usuario nao encontrado: " + id);
        }

        try {

            usuarioRepository.deleteById(id);

        } catch (DataIntegrityViolationException ex) {

            throw new DataBindingViolationException(
                    "Nao e possivel excluir pois ha entidades relacionadas");
        }
    }
}