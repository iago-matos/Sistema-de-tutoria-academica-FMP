package br.com.tutoria.service;
import br.com.tutoria.dto.CadastroRequestDTO;
import br.com.tutoria.dto.UsuarioMeDTO;
import br.com.tutoria.dto.UsuarioResumoDTO;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.ProfileEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
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
import java.util.Locale;

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

    // Evita contas duplicadas como "Ana@x.com" e "ana@x.com"
    public static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // Cadastro publico: cria o usuario e o perfil (estudante ou tutor) juntos.
    // A conta nasce PENDENTE e so faz login depois que um administrador aprovar.
    @Transactional
    public UsuarioResumoDTO cadastrar(CadastroRequestDTO dados) {

        if (dados.tipo() == UsuarioTipoEnum.ADMIN) {
            throw new IllegalArgumentException("O cadastro só pode ser feito como ESTUDANTE ou TUTOR");
        }

        // Valida tudo antes de gravar qualquer coisa
        boolean estudante = dados.tipo() == UsuarioTipoEnum.ESTUDANTE;
        String campoDoPerfil = estudante
                ? exigirTexto(dados.diaDaSemana(), "Informe o dia da semana de disponibilidade do estudante")
                : exigirTexto(dados.disciplina(), "Informe a disciplina do tutor");

        String email = normalizarEmail(dados.email());

        usuarioRepository.findByEmail(email).ifPresent(u -> {
            throw new IllegalArgumentException("Email ja cadastrado: " + email);
        });

        Usuario usuario = new Usuario();
        usuario.setNome(dados.nome().trim());
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(dados.senha()));
        usuario.getProfiles().add(ProfileEnum.USER);
        usuario.setStatus(UsuarioStatusEnum.PENDENTE);
        usuarioRepository.save(usuario);

        if (estudante) {
            Estudante perfil = new Estudante();
            perfil.setUsuario(usuario);
            perfil.setDiaDaSemana(campoDoPerfil);
            estudanteRepository.save(perfil);
        } else {
            Tutor perfil = new Tutor();
            perfil.setUsuario(usuario);
            perfil.setDisciplina(campoDoPerfil);
            tutorRepository.save(perfil);
        }

        return paraResumo(usuario, dados.tipo());
    }

    @Transactional(readOnly = true)
    public List<UsuarioResumoDTO> listarPendentes() {
        return usuarioRepository.findByStatusOrderByIdAsc(UsuarioStatusEnum.PENDENTE).stream()
                .map(usuario -> paraResumo(usuario, resolverTipo(usuario)))
                .toList();
    }

    @Transactional
    public UsuarioResumoDTO aprovar(Long id) {
        Usuario usuario = buscarPorId(id);

        if (usuario.getStatus() == UsuarioStatusEnum.ATIVO) {
            throw new IllegalArgumentException("Este usuario ja esta ativo");
        }

        usuario.setStatus(UsuarioStatusEnum.ATIVO);
        usuarioRepository.save(usuario);
        return paraResumo(usuario, resolverTipo(usuario));
    }

    @Transactional
    public UsuarioResumoDTO recusar(Long id) {
        Usuario usuario = buscarPorId(id);

        if (usuario.getStatus() != UsuarioStatusEnum.PENDENTE) {
            throw new IllegalArgumentException("Apenas cadastros pendentes podem ser recusados");
        }

        usuario.setStatus(UsuarioStatusEnum.RECUSADO);
        usuarioRepository.save(usuario);
        return paraResumo(usuario, resolverTipo(usuario));
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

        String novoEmail = normalizarEmail(dados.getEmail());

        if (novoEmail != null
                && !novoEmail.equals(existente.getEmail())) {

            usuarioRepository.findByEmail(novoEmail).ifPresent(u -> {
                throw new IllegalArgumentException(
                        "Email ja cadastrado: " + novoEmail);
            });
        }

        if (dados.getNome() != null) {
            existente.setNome(dados.getNome());
        }

        if (novoEmail != null) {
            existente.setEmail(novoEmail);
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

    private static String exigirTexto(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        return valor.trim();
    }

    private static UsuarioResumoDTO paraResumo(Usuario usuario, UsuarioTipoEnum tipo) {
        return new UsuarioResumoDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getStatus(),
                tipo);
    }
}
