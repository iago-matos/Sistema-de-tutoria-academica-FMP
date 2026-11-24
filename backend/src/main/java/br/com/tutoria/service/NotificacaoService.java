package br.com.tutoria.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.dto.NotificacaoDTO;
import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Avaliacao;
import br.com.tutoria.model.enums.AgendamentoStatusEnum;
import br.com.tutoria.model.enums.TutorDisciplinaStatusEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.repository.AvaliacaoRepository;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.TutorDisciplinaRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.repository.UsuarioRepository;

// Avisos do sino de notificacoes. Nada e gravado: cada aviso e calculado a partir
// do estado atual, entao ele some sozinho quando a pendencia e resolvida
// (o tutor confirma a solicitacao, o estudante le a mensagem, avalia a tutoria...).
@Service
public class NotificacaoService {

    // Tutorias confirmadas que aparecem como lembrete: hoje e amanha
    private static final int DIAS_DE_LEMBRETE = 1;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ConversaService conversaService;
    private final AgendamentoRepository agendamentoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final TutorRepository tutorRepository;
    private final EstudanteRepository estudanteRepository;
    private final UsuarioRepository usuarioRepository;
    private final TutorDisciplinaRepository tutorDisciplinaRepository;
    private final AutorizacaoService autorizacaoService;
    private final Clock clock;

    public NotificacaoService(
            ConversaService conversaService,
            AgendamentoRepository agendamentoRepository,
            AvaliacaoRepository avaliacaoRepository,
            TutorRepository tutorRepository,
            EstudanteRepository estudanteRepository,
            UsuarioRepository usuarioRepository,
            TutorDisciplinaRepository tutorDisciplinaRepository,
            AutorizacaoService autorizacaoService,
            Clock clock) {
        this.conversaService = conversaService;
        this.agendamentoRepository = agendamentoRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.tutorRepository = tutorRepository;
        this.estudanteRepository = estudanteRepository;
        this.usuarioRepository = usuarioRepository;
        this.tutorDisciplinaRepository = tutorDisciplinaRepository;
        this.autorizacaoService = autorizacaoService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NotificacaoDTO> listar(UserSpringSecurity usuarioLogado) {
        List<NotificacaoDTO> avisos = new ArrayList<>();
        LocalDateTime agora = LocalDateTime.now(clock);

        if (autorizacaoService.isAdmin(usuarioLogado)) {
            avisosDaCoordenacao(avisos);
            return avisos;
        }

        long naoLidas = conversaService.totalNaoLidas(usuarioLogado.getId());
        if (naoLidas > 0) {
            avisos.add(new NotificacaoDTO("MENSAGEM",
                    naoLidas == 1 ? "1 mensagem não lida" : naoLidas + " mensagens não lidas",
                    "Abra o chat para responder.", "mensagens.html", naoLidas));
        }

        tutorRepository.findByUsuario_Id(usuarioLogado.getId())
                .ifPresent(tutor -> avisosDoTutor(avisos,
                        agendamentoRepository.findByTutor_IdOrderByDataAscHoraAsc(tutor.getId()), agora));

        estudanteRepository.findByUsuario_Id(usuarioLogado.getId())
                .ifPresent(estudante -> avisosDoEstudante(avisos,
                        agendamentoRepository.findByEstudante_IdOrderByDataAscHoraAsc(estudante.getId()),
                        avaliacaoRepository.findByEstudante_Id(estudante.getId()), agora));

        return avisos;
    }

    private void avisosDaCoordenacao(List<NotificacaoDTO> avisos) {
        int cadastros = usuarioRepository.findByStatusOrderByIdAsc(UsuarioStatusEnum.PENDENTE).size();
        if (cadastros > 0) {
            avisos.add(new NotificacaoDTO("CADASTRO_PENDENTE",
                    cadastros == 1 ? "1 cadastro aguardando aprovação" : cadastros + " cadastros aguardando aprovação",
                    "Aprove ou recuse os novos tutores.", "aprovacoes.html", cadastros));
        }

        int pedidos = tutorDisciplinaRepository.findByStatusComTutor(TutorDisciplinaStatusEnum.PENDENTE).size();
        if (pedidos > 0) {
            avisos.add(new NotificacaoDTO("DISCIPLINA_PENDENTE",
                    pedidos == 1 ? "1 pedido de disciplina" : pedidos + " pedidos de disciplina",
                    "Tutores pediram para atender novas disciplinas.", "aprovacoes.html", pedidos));
        }
    }

    private void avisosDoTutor(List<NotificacaoDTO> avisos, List<Agendamento> agenda, LocalDateTime agora) {
        long pendentes = agenda.stream()
                .filter(a -> a.getStatus() == AgendamentoStatusEnum.PENDENTE && !a.comecouAte(agora))
                .count();
        if (pendentes > 0) {
            avisos.add(new NotificacaoDTO("SOLICITACAO_PENDENTE",
                    pendentes == 1 ? "1 solicitação aguardando você" : pendentes + " solicitações aguardando você",
                    "Confirme ou recuse os pedidos de tutoria.", "solicitacoes.html", pendentes));
        }

        proximasConfirmadas(agenda, agora).forEach(a -> avisos.add(new NotificacaoDTO("LEMBRETE",
                "Tutoria " + quando(a, agora.toLocalDate()),
                "Com " + nomeDoEstudante(a) + disciplina(a) + ".", "agenda.html", 0)));
    }

    private void avisosDoEstudante(List<NotificacaoDTO> avisos, List<Agendamento> agenda,
            List<Avaliacao> avaliacoes, LocalDateTime agora) {

        proximasConfirmadas(agenda, agora).forEach(a -> avisos.add(new NotificacaoDTO("LEMBRETE",
                "Tutoria confirmada " + quando(a, agora.toLocalDate()),
                "Com " + a.getTutor().getUsuario().getNome() + disciplina(a) + ".",
                "meus-agendamentos.html", 0)));

        // Mesma regra do AvaliacaoService: tutoria que ja aconteceu e ainda nao foi avaliada
        Set<Long> avaliadas = avaliacoes.stream()
                .map(Avaliacao::getAgendamentoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        long paraAvaliar = agenda.stream()
                .filter(a -> (a.getStatus() == AgendamentoStatusEnum.CONFIRMADO
                        || a.getStatus() == AgendamentoStatusEnum.REALIZADO)
                        && a.comecouAte(agora)
                        && !avaliadas.contains(a.getId()))
                .count();
        if (paraAvaliar > 0) {
            avisos.add(new NotificacaoDTO("AVALIACAO_PENDENTE",
                    paraAvaliar == 1 ? "1 tutoria para avaliar" : paraAvaliar + " tutorias para avaliar",
                    "Conte como foi: a avaliação ajuda outros estudantes.", "avaliacoes.html", paraAvaliar));
        }
    }

    // Confirmadas que ainda nao comecaram, de hoje ate amanha
    private static List<Agendamento> proximasConfirmadas(List<Agendamento> agenda, LocalDateTime agora) {
        LocalDate limite = agora.toLocalDate().plusDays(DIAS_DE_LEMBRETE);
        return agenda.stream()
                .filter(a -> a.getStatus() == AgendamentoStatusEnum.CONFIRMADO
                        && !a.comecouAte(agora)
                        && !a.getData().isAfter(limite))
                .toList();
    }

    private static String quando(Agendamento a, LocalDate hoje) {
        String dia = a.getData().equals(hoje) ? "hoje"
                : a.getData().equals(hoje.plusDays(1)) ? "amanhã"
                : "em " + a.getData().format(DATA);
        return dia + " às " + a.getHora().format(HORA);
    }

    private static String nomeDoEstudante(Agendamento a) {
        return a.getEstudante() == null ? "estudante" : a.getEstudante().getUsuario().getNome();
    }

    private static String disciplina(Agendamento a) {
        return a.getDisciplina() == null ? "" : " · " + a.getDisciplina().getNome();
    }
}
