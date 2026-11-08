package br.com.tutoria.configs;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Avaliacao;
import br.com.tutoria.model.Disciplina;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.ModalidadeEnum;
import br.com.tutoria.model.enums.ProfileEnum;
import br.com.tutoria.model.enums.TutorDisciplinaStatusEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.service.DisciplinaService;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.repository.AvaliacaoRepository;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;

// SO PARA TESTES E DEMONSTRACAO. Desligado por padrao (DEMO_DADOS=false).
//
// Com DEMO_DADOS=true, cria ao subir um estudante que ja fez tutorias com
// quatro tutores, para ver a tela "Avaliar tutoria" funcionando:
//   - Ana (Matematica): 2 tutorias para avaliar
//   - Bruno (Fisica): 1 tutoria ontem para avaliar, e outra daqui a 3 dias (ainda nao conta)
//   - Carla (Programacao): 1 tutoria ja avaliada (aparece em cinza)
//   - Diego (Banco de Dados): 1 tutoria para avaliar
//   - Elisa (Estatistica): tutoria daqui a 2 dias, aguardando o tutor aceitar (bloqueado)
// Todos entram com e-mail e senha (DEMO_SENHA). Roda uma vez so: se o
// estudante demo ja existir, nada e criado de novo.
// Com DEMO_ESTUDANTE_EMAIL, a sua conta de estudante ganha as mesmas tutorias.
@Slf4j
@Component
public class DadosDemoSeeder implements ApplicationRunner {

    public static final String EMAIL_ESTUDANTE = "estudante.demo@exemplo.com";
    private static final String SUFIXO_TUTOR_DEMO = ".demo@exemplo.com";
    private static final int TAMANHO_MINIMO_SENHA = 8;

    private final UsuarioRepository usuarioRepository;
    private final EstudanteRepository estudanteRepository;
    private final TutorRepository tutorRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final DisciplinaService disciplinaService;
    private final boolean ativo;
    private final String senha;
    private final String estudanteExtra;

    public DadosDemoSeeder(
            UsuarioRepository usuarioRepository,
            EstudanteRepository estudanteRepository,
            TutorRepository tutorRepository,
            AgendamentoRepository agendamentoRepository,
            AvaliacaoRepository avaliacaoRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            DisciplinaService disciplinaService,
            @Value("${app.demo.ativo:false}") boolean ativo,
            @Value("${app.demo.senha:}") String senha,
            @Value("${app.demo.estudante-email:}") String estudanteExtra) {
        this.usuarioRepository = usuarioRepository;
        this.estudanteRepository = estudanteRepository;
        this.tutorRepository = tutorRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.disciplinaService = disciplinaService;
        this.ativo = ativo;
        this.senha = senha;
        this.estudanteExtra = estudanteExtra;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!ativo) {
            return;
        }
        if (senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new IllegalStateException("DEMO_SENHA precisa ter no minimo "
                    + TAMANHO_MINIMO_SENHA + " caracteres quando DEMO_DADOS=true");
        }
        log.warn("DEMO_DADOS=true: dados de demonstracao ativos. Desligue antes de usar com alunos reais.");

        if (usuarioRepository.findByEmail(EMAIL_ESTUDANTE).isPresent()) {
            log.info("Estudante demo ja existe ({}): nada a criar", EMAIL_ESTUDANTE);
        } else {
            Estudante estudante = new Estudante();
            estudante.setUsuario(criarUsuario("Estudante Demo", EMAIL_ESTUDANTE));
            estudanteRepository.save(estudante);
            criarTutorias(estudante);
            log.info("Dados de demonstracao criados. Entre como {} (senha: DEMO_SENHA)", EMAIL_ESTUDANTE);
        }

        if (!estudanteExtra.isBlank()) {
            darTutoriasA(estudanteExtra.trim().toLowerCase());
        }
    }

    // DEMO_ESTUDANTE_EMAIL: a sua propria conta de estudante (ja cadastrada)
    // ganha as mesmas tutorias de teste, para ver os perfis logado como voce.
    private void darTutoriasA(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        Estudante estudante = usuario == null ? null
                : estudanteRepository.findByUsuario_Id(usuario.getId()).orElse(null);
        if (estudante == null) {
            log.warn("DEMO_ESTUDANTE_EMAIL={}: nenhuma conta de estudante com esse e-mail. "
                    + "Cadastre-se como estudante e reinicie a aplicacao.", email);
            return;
        }
        boolean jaTem = agendamentoRepository.findByEstudante_Id(estudante.getId()).stream()
                .anyMatch(a -> a.getTutor().getUsuario().getEmail().endsWith(SUFIXO_TUTOR_DEMO));
        if (jaTem) {
            log.info("{} ja tem tutorias de demonstracao: nada a criar", email);
            return;
        }
        criarTutorias(estudante);
        log.info("Tutorias de demonstracao adicionadas a conta {}", email);
    }

    private void criarTutorias(Estudante estudante) {
        LocalDate hoje = LocalDate.now(clock);

        Tutor ana = tutorDemo("Ana Souza", "ana", "Matemática");
        Tutor bruno = tutorDemo("Bruno Lima", "bruno", "Física");
        Tutor carla = tutorDemo("Carla Mendes", "carla", "Programação");
        Tutor diego = tutorDemo("Diego Rocha", "diego", "Banco de Dados");

        tutoriaPassada(ana, estudante, hoje.minusDays(2), LocalTime.of(14, 0), true);
        tutoriaPassada(ana, estudante, hoje.minusDays(9), LocalTime.of(10, 0), false);
        tutoriaPassada(bruno, estudante, hoje.minusDays(1), LocalTime.of(16, 0), false);
        tutoriaFutura(bruno, estudante, hoje.plusDays(3), LocalTime.of(16, 0));
        Agendamento daCarla = tutoriaPassada(carla, estudante, hoje.minusDays(5), LocalTime.of(9, 0), true);
        tutoriaPassada(diego, estudante, hoje.minusDays(12), LocalTime.of(19, 0), true);

        avaliar(daCarla, 5, LocalDateTime.of(hoje.minusDays(4), LocalTime.of(20, 15)));

        // So agendada, o tutor ainda nao aceitou: o perfil aparece bloqueado
        Tutor elisa = tutorDemo("Elisa Prado", "elisa", "Estatística");
        Agendamento daElisa = Agendamento.disponibilizar(elisa, hoje.plusDays(2), LocalTime.of(11, 0), "Tutoria de demonstração");
        daElisa.agendar(estudante, ModalidadeEnum.PRESENCIAL, disciplinaDe(elisa));
        agendamentoRepository.save(daElisa);
    }

    // Reaproveita o tutor demo se ele ja existir (o estudante extra usa os mesmos)
    private Tutor tutorDemo(String nome, String apelido, String disciplina) {
        String email = "tutor." + apelido + SUFIXO_TUTOR_DEMO;
        return usuarioRepository.findByEmail(email)
                .flatMap(u -> tutorRepository.findAll().stream()
                        .filter(t -> t.getUsuario().getId().equals(u.getId())).findFirst())
                .orElseGet(() -> criarTutor(nome, email, disciplina));
    }

    private Usuario criarUsuario(String nome, String email) {
        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.getProfiles().add(ProfileEnum.USER);
        usuario.setStatus(UsuarioStatusEnum.ATIVO);
        return usuarioRepository.save(usuario);
    }

    private Tutor criarTutor(String nome, String email, String disciplina) {
        Tutor tutor = new Tutor();
        tutor.setUsuario(criarUsuario(nome, email));
        tutorRepository.save(tutor);
        disciplinaService.vincular(tutor, disciplinaService.buscarOuCriar(disciplina), TutorDisciplinaStatusEnum.ATIVA);
        return tutor;
    }

    // Os tutores demo atendem uma disciplina so: a tutoria e dela
    private static Disciplina disciplinaDe(Tutor tutor) {
        return tutor.getDisciplinasAtivas().stream().findFirst().orElse(null);
    }

    // Pelos metodos da entidade, como no uso real: disponibilizar -> agendar -> confirmar (-> concluir)
    private Agendamento tutoriaPassada(Tutor tutor, Estudante estudante, LocalDate data, LocalTime hora,
            boolean realizada) {
        Agendamento tutoria = Agendamento.disponibilizar(tutor, data, hora, "Tutoria de demonstração");
        tutoria.agendar(estudante, ModalidadeEnum.PRESENCIAL, disciplinaDe(tutor));
        tutoria.confirmar();
        if (realizada) {
            tutoria.concluir();
        }
        return agendamentoRepository.save(tutoria);
    }

    private void tutoriaFutura(Tutor tutor, Estudante estudante, LocalDate data, LocalTime hora) {
        Agendamento tutoria = Agendamento.disponibilizar(tutor, data, hora, "Tutoria de demonstração");
        tutoria.agendar(estudante, ModalidadeEnum.PRESENCIAL, disciplinaDe(tutor));
        tutoria.confirmar();
        agendamentoRepository.save(tutoria);
    }

    private void avaliar(Agendamento tutoria, int nota, LocalDateTime quando) {
        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setAgendamento(tutoria);
        avaliacao.setTutor(tutoria.getTutor());
        avaliacao.setEstudante(tutoria.getEstudante());
        avaliacao.setNota(nota);
        avaliacao.setComentario("Explicou recursão com exemplos muito claros. Ajudou bastante na prova!");
        avaliacao.setDataAvaliacao(quando.toLocalDate());
        avaliacao.setHoraAvaliacao(quando.toLocalTime());
        avaliacaoRepository.save(avaliacao);
    }
}
