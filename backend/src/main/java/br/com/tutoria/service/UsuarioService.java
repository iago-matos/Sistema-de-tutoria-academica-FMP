package br.com.tutoria.service;
import br.com.tutoria.dto.AlterarSenhaDTO;
import br.com.tutoria.dto.AtualizarUsuarioDTO;
import br.com.tutoria.dto.CadastroRequestDTO;
import br.com.tutoria.dto.UsuarioMeDTO;
import br.com.tutoria.dto.UsuarioResumoDTO;
import br.com.tutoria.model.Disciplina;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.ProfileEnum;
import br.com.tutoria.model.enums.TutorDisciplinaStatusEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.model.enums.UsuarioTipoEnum;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.repository.UsuarioRepository;
import br.com.tutoria.service.exceptions.AcessoRecusadoException;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UsuarioService {

    public static final String NOME_ANONIMIZADO = "Usuário removido";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EstudanteRepository estudanteRepository;
    private final TutorRepository tutorRepository;
    private final DisciplinaService disciplinaService;
    private final boolean cadastroComSenhaHabilitado;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            EstudanteRepository estudanteRepository,
            TutorRepository tutorRepository,
            DisciplinaService disciplinaService,
            @Value("${app.cadastro-com-senha:false}") boolean cadastroComSenhaHabilitado) {

        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.estudanteRepository = estudanteRepository;
        this.tutorRepository = tutorRepository;
        this.disciplinaService = disciplinaService;
        this.cadastroComSenhaHabilitado = cadastroComSenhaHabilitado;
    }

    // Evita contas duplicadas como "Ana@x.com" e "ana@x.com"
    public static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // Cadastro publico com e-mail e senha. A conta nasce PENDENTE e so faz login
    // depois que um administrador aprovar. Desligado por padrao (CADASTRO_COM_SENHA):
    // contas novas entram pelo Google, que comprova que a pessoa e dona do e-mail.
    @Transactional
    public UsuarioResumoDTO cadastrar(CadastroRequestDTO dados) {

        if (!cadastroComSenhaHabilitado) {
            throw new AcessoRecusadoException(HttpStatus.FORBIDDEN,
                    "O cadastro é feito com a conta Google institucional, na tela de login.");
        }

        Usuario usuario = criarComPerfil(dados.nome(), dados.email(), dados.senha(),
                dados.tipo(), dados.disciplinaId(), UsuarioStatusEnum.PENDENTE);

        return paraResumo(usuario, dados.tipo());
    }

    public boolean isCadastroComSenhaHabilitado() {
        return cadastroComSenhaHabilitado;
    }

    // Cria o usuario e o perfil (estudante ou tutor) juntos. Usado pelo cadastro
    // com senha e pelo primeiro acesso com Google. O estudante so precisa do tipo:
    // os dias das sessoes vem da agenda do tutor. O tutor escolhe uma disciplina
    // da lista; ela nasce ATIVA, porque a coordenacao a aprova junto com a conta.
    @Transactional
    public Usuario criarComPerfil(String nome, String email, String senha, UsuarioTipoEnum tipo,
            Long disciplinaId, UsuarioStatusEnum status) {

        if (tipo != UsuarioTipoEnum.ESTUDANTE && tipo != UsuarioTipoEnum.TUTOR) {
            throw new IllegalArgumentException("O cadastro só pode ser feito como ESTUDANTE ou TUTOR");
        }

        // Valida tudo antes de gravar qualquer coisa
        boolean estudante = tipo == UsuarioTipoEnum.ESTUDANTE;
        if (!estudante && disciplinaId == null) {
            throw new IllegalArgumentException("Escolha a disciplina do tutor");
        }
        Disciplina disciplinaDoTutor = estudante ? null : disciplinaService.buscar(disciplinaId);

        String emailNormalizado = normalizarEmail(email);

        usuarioRepository.findByEmail(emailNormalizado).ifPresent(u -> {
            throw new IllegalArgumentException("Email ja cadastrado: " + emailNormalizado);
        });

        Usuario usuario = new Usuario();
        usuario.setNome(exigirTexto(nome, "Nome é obrigatório"));
        usuario.setEmail(emailNormalizado);
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.getProfiles().add(ProfileEnum.USER);
        usuario.setStatus(status);
        usuarioRepository.save(usuario);

        if (estudante) {
            Estudante perfil = new Estudante();
            perfil.setUsuario(usuario);
            estudanteRepository.save(perfil);
        } else {
            Tutor perfil = new Tutor();
            perfil.setUsuario(usuario);
            tutorRepository.save(perfil);
            disciplinaService.vincular(perfil, disciplinaDoTutor, TutorDisciplinaStatusEnum.ATIVA);
        }

        return usuario;
    }

    // Tutores pendentes vem com a disciplina escolhida no cadastro
    @Transactional(readOnly = true)
    public List<UsuarioResumoDTO> listarPendentes() {
        return usuarioRepository.findByStatusOrderByIdAsc(UsuarioStatusEnum.PENDENTE).stream()
                .map(usuario -> {
                    UsuarioTipoEnum tipo = resolverTipo(usuario);
                    List<String> disciplinas = tipo != UsuarioTipoEnum.TUTOR ? null
                            : tutorRepository.findByUsuario_Id(usuario.getId())
                                    .map(t -> disciplinaService.ativasDoTutor(t.getId()).stream()
                                            .map(Disciplina::getNome).toList())
                                    .orElse(List.of());
                    return new UsuarioResumoDTO(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                            usuario.getStatus(), tipo, disciplinas);
                })
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

    // Desfaz a inativacao. As sessoes canceladas na inativacao nao voltam:
    // o tutor abre horarios novos na agenda.
    @Transactional
    public UsuarioResumoDTO reativar(Long id) {
        Usuario usuario = buscarPorId(id);

        if (usuario.getStatus() != UsuarioStatusEnum.INATIVO) {
            throw new IllegalArgumentException("Apenas contas inativas podem ser reativadas");
        }

        usuario.setStatus(UsuarioStatusEnum.ATIVO);
        usuarioRepository.save(usuario);
        return paraResumo(usuario, resolverTipo(usuario));
    }

    // Apaga os dados pessoais a pedido do proprio aluno ou tutor. O pedido e feito
    // pessoalmente na secretaria, e um coordenador (admin) executa aqui.
    //
    // A conta precisa estar INATIVA antes: a inativacao ja desfez os compromissos
    // futuros. Aqui so se apaga a identidade. Sessoes passadas, avaliacoes e
    // mensagens do chat continuam, mas passam a aparecer como "Usuário removido".
    // O e-mail original fica livre para um cadastro novo no futuro.
    @Transactional
    public UsuarioResumoDTO anonimizar(Long id) {
        Usuario usuario = buscarPorId(id);

        if (usuario.getStatus() != UsuarioStatusEnum.INATIVO) {
            throw new IllegalArgumentException("Inative a conta antes de apagar os dados pessoais");
        }

        usuario.setNome(NOME_ANONIMIZADO);
        // Unico e com formato valido; o dominio .invalid nunca recebe e-mails (RFC 2606)
        usuario.setEmail("removido-" + usuario.getId() + "@anonimizado.invalid");
        // Senha aleatoria que ninguem conhece: a conta nunca mais faz login
        usuario.setSenha(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setStatus(UsuarioStatusEnum.ANONIMIZADO);
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

    // Lista para a tela de gestao: cada usuario com o seu tipo (tutor, estudante...)
    @Transactional(readOnly = true)
    public List<UsuarioResumoDTO> listarTodos() {
        Set<Long> tutores = tutorRepository.findTodosUsuarioIds();
        Set<Long> estudantes = estudanteRepository.findTodosUsuarioIds();

        return usuarioRepository.findAll().stream()
                .map(usuario -> paraResumo(usuario, tipoPorPerfis(usuario, tutores, estudantes)))
                .toList();
    }

    private static UsuarioTipoEnum tipoPorPerfis(Usuario usuario, Set<Long> tutores, Set<Long> estudantes) {
        if (usuario.getProfiles().contains(ProfileEnum.ADMIN)) {
            return UsuarioTipoEnum.ADMIN;
        }
        return tutores.contains(usuario.getId()) ? UsuarioTipoEnum.TUTOR : UsuarioTipoEnum.ESTUDANTE;
    }

    @Transactional(readOnly = true)
    public UsuarioMeDTO buscarMe(Long usuarioId) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() ->
                        new ObjectNotFoundException(
                                "Usuario nao encontrado: " + usuarioId));

        UsuarioTipoEnum tipo = resolverTipo(usuario);

        Long estudanteId = estudanteRepository.findByUsuario_Id(usuario.getId())
                .map(Estudante::getId).orElse(null);
        Long tutorId = tutorRepository.findByUsuario_Id(usuario.getId())
                .map(Tutor::getId).orElse(null);

        return new UsuarioMeDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                tipo,
                estudanteId,
                tutorId);
    }

    // Ser coordenador nao torna ninguem administrador: so o perfil ADMIN
    private UsuarioTipoEnum resolverTipo(Usuario usuario) {

        if (usuario.getProfiles().contains(ProfileEnum.ADMIN)) {
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
    public Usuario atualizar(Long id, AtualizarUsuarioDTO dados) {

        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() ->
                        new ObjectNotFoundException(
                                "Usuario nao encontrado: " + id));

        // Devolver nome ou e-mail a uma conta anonimizada desfaria o pedido do titular
        if (existente.getStatus() == UsuarioStatusEnum.ANONIMIZADO) {
            throw new IllegalArgumentException("Os dados desta conta foram apagados e ela não pode ser editada");
        }

        String novoEmail = normalizarEmail(dados.email());

        if (novoEmail != null
                && !novoEmail.equals(existente.getEmail())) {

            usuarioRepository.findByEmail(novoEmail).ifPresent(u -> {
                throw new IllegalArgumentException(
                        "Email ja cadastrado: " + novoEmail);
            });
        }

        if (dados.nome() != null) {
            existente.setNome(exigirTexto(dados.nome(), "Nome não pode ficar em branco"));
        }

        if (novoEmail != null) {
            existente.setEmail(novoEmail);
        }

        return usuarioRepository.save(existente);
    }

    @Transactional
    public void alterarSenha(Long usuarioId, AlterarSenhaDTO dados) {

        Usuario usuario = buscarPorId(usuarioId);

        if (!passwordEncoder.matches(dados.senhaAtual(), usuario.getSenha())) {
            throw new IllegalArgumentException("Senha atual incorreta");
        }

        if (passwordEncoder.matches(dados.novaSenha(), usuario.getSenha())) {
            throw new IllegalArgumentException("A nova senha deve ser diferente da atual");
        }

        usuario.setSenha(passwordEncoder.encode(dados.novaSenha()));
        usuarioRepository.save(usuario);
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
