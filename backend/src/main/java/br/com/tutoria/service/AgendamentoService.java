package br.com.tutoria.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.dto.AtualizarAgendamentoDTO;
import br.com.tutoria.dto.LinkReuniaoDTO;
import br.com.tutoria.dto.NovoHorarioDTO;
import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Disciplina;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.enums.AgendamentoStatusEnum;
import br.com.tutoria.model.enums.ModalidadeEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.service.exceptions.DataBindingViolationException;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;

// Orquestra o Agendamento: descobre quem esta logado, confere permissao,
// conflito de horario e relogio, e delega a mudanca de estado a propria entidade.
@Service
public class AgendamentoService {

    private static final Set<AgendamentoStatusEnum> OCUPAM_O_ESTUDANTE =
            Set.of(AgendamentoStatusEnum.PENDENTE, AgendamentoStatusEnum.CONFIRMADO);

    private final AgendamentoRepository agendamentoRepository;
    private final TutorRepository tutorRepository;
    private final EstudanteRepository estudanteRepository;
    private final AutorizacaoService autorizacaoService;
    private final DisciplinaService disciplinaService;
    private final Clock clock;

    public AgendamentoService(
            AgendamentoRepository agendamentoRepository,
            TutorRepository tutorRepository,
            EstudanteRepository estudanteRepository,
            AutorizacaoService autorizacaoService,
            DisciplinaService disciplinaService,
            Clock clock) {
        this.agendamentoRepository = agendamentoRepository;
        this.tutorRepository = tutorRepository;
        this.estudanteRepository = estudanteRepository;
        this.autorizacaoService = autorizacaoService;
        this.disciplinaService = disciplinaService;
        this.clock = clock;
    }

    // ── Tutor abre um horario na agenda ──────────────────────────────────────
    // Tutores criam sempre na propria agenda (tutorId do corpo e ignorado).
    // Administradores podem criar para qualquer tutor, informando tutorId.
    @Transactional
    public Agendamento disponibilizar(NovoHorarioDTO dados, UserSpringSecurity usuarioLogado) {
        Tutor tutor = autorizacaoService.isAdmin(usuarioLogado)
                ? resolverTutor(dados.tutorId())
                : autorizacaoService.tutorLogado(usuarioLogado);

        Agendamento novo = Agendamento.disponibilizar(
                tutor, dados.data(), dados.hora(), dados.observacoes(), dados.linkReuniao());
        exigirNoFuturo(novo);
        exigirAgendaDoTutorLivre(novo);

        return agendamentoRepository.save(novo);
    }

    // ── Estudante solicita um horario disponivel ─────────────────────────────
    // O estudante e sempre quem esta logado, nunca um ID enviado pelo cliente
    @Transactional
    public Agendamento solicitar(Long agendamentoId, String modalidade, Long disciplinaId,
            UserSpringSecurity usuarioLogado) {
        Estudante estudante = autorizacaoService.estudanteLogado(usuarioLogado);
        Agendamento agendamento = buscarParaAlterar(agendamentoId);

        exigirNoFuturo(agendamento);
        exigirAgendaDoEstudanteLivre(estudante, agendamento);
        Disciplina disciplina = escolherDisciplina(agendamento.getTutor(), disciplinaId);

        if (!agendamento.agendar(estudante, converterModalidade(modalidade), disciplina)) {
            throw new IllegalArgumentException("Este horário não está mais disponível");
        }
        agendamentoRepository.save(agendamento);
        return buscarPorId(agendamentoId);
    }

    // A disciplina precisa estar entre as ativas do tutor. Sem disciplinaId,
    // vale a unica que o tutor atende; com mais de uma, o estudante escolhe.
    private Disciplina escolherDisciplina(Tutor tutor, Long disciplinaId) {
        List<Disciplina> ativas = disciplinaService.ativasDoTutor(tutor.getId());
        if (disciplinaId == null) {
            if (ativas.size() > 1) {
                throw new IllegalArgumentException("Escolha a disciplina da tutoria");
            }
            return ativas.isEmpty() ? null : ativas.get(0);
        }
        return ativas.stream()
                .filter(d -> d.getId().equals(disciplinaId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Este tutor não atende essa disciplina"));
    }

    // ── Cancelar ─────────────────────────────────────────────────────────────
    // Tutor da sessao ou admin: a sessao fica CANCELADA.
    // Estudante da sessao: ele desiste e o horario volta a ficar DISPONIVEL,
    // mas so ate a vespera. No dia da tutoria o tutor ja se preparou.
    @Transactional
    public Agendamento cancelar(Long id, UserSpringSecurity usuarioLogado) {
        Agendamento agendamento = buscarParaAlterar(id);

        if (autorizacaoService.isAdmin(usuarioLogado) || isTutorDoAgendamento(agendamento, usuarioLogado)) {
            agendamento.cancelar();
        } else if (isEstudanteDoAgendamento(agendamento, usuarioLogado)) {
            exigirAntesDoDiaDaSessao(agendamento);
            agendamento.desistir();
        } else {
            throw new AccessDeniedException("Você não participa desta sessão");
        }

        agendamentoRepository.save(agendamento);
        return buscarPorId(id);
    }

    // ── Tutor confirma, recusa, conclui, remarca ou edita observacoes ────────
    @Transactional
    public Agendamento atualizar(Long id, AtualizarAgendamentoDTO dados, UserSpringSecurity usuarioLogado) {
        Agendamento agendamento = buscarParaAlterar(id);

        if (!autorizacaoService.isAdmin(usuarioLogado) && !isTutorDoAgendamento(agendamento, usuarioLogado)) {
            throw new AccessDeniedException("Apenas o tutor responsável pela sessão pode alterá-la");
        }

        if (dados.status() != null) {
            aplicarStatus(agendamento, converterStatus(dados.status()));
        }
        if (dados.data() != null || dados.hora() != null) {
            agendamento.remarcar(
                    dados.data() != null ? dados.data() : agendamento.getData(),
                    dados.hora() != null ? dados.hora() : agendamento.getHora());
            exigirNoFuturo(agendamento);
            exigirAgendaDoTutorLivre(agendamento);
        }
        if (dados.observacoes() != null) {
            agendamento.alterarObservacoes(dados.observacoes());
        }
        if (dados.linkReuniao() != null) {
            agendamento.alterarLinkReuniao(dados.linkReuniao());
        }

        agendamentoRepository.save(agendamento);
        return buscarPorId(id);
    }

    private void aplicarStatus(Agendamento agendamento, AgendamentoStatusEnum novo) {
        switch (novo) {
            case CONFIRMADO -> {
                exigirNoFuturo(agendamento);
                agendamento.confirmar();
            }
            case CANCELADO -> agendamento.cancelar();
            case REALIZADO -> {
                if (!agendamento.comecouAte(agora())) {
                    throw new IllegalArgumentException("Só é possível concluir uma sessão depois que ela começar");
                }
                agendamento.concluir();
            }
            default -> throw new IllegalArgumentException(
                    "Status " + novo + " não pode ser definido manualmente. Use CONFIRMADO, CANCELADO ou REALIZADO");
        }
    }

    // ── Link da videoconferencia ─────────────────────────────────────────────
    // Tutor da sessao e admin: sempre. Estudante da sessao: so depois que o
    // tutor confirmar a tutoria online. Os demais nunca veem o link.
    @Transactional(readOnly = true)
    public LinkReuniaoDTO linkReuniao(Long id, UserSpringSecurity usuarioLogado) {
        Agendamento agendamento = buscarPorId(id);

        if (autorizacaoService.isAdmin(usuarioLogado) || isTutorDoAgendamento(agendamento, usuarioLogado)) {
            return new LinkReuniaoDTO(agendamento.getLinkReuniao());
        }
        if (isEstudanteDoAgendamento(agendamento, usuarioLogado)) {
            if (!agendamento.linkLiberadoParaEstudante()) {
                throw new AccessDeniedException(
                        "O link é liberado quando o tutor confirmar a tutoria online");
            }
            return new LinkReuniaoDTO(agendamento.getLinkReuniao());
        }
        throw new AccessDeniedException("Você não participa desta sessão");
    }

    // ── Visualizar ───────────────────────────────────────────────────────────

    // Diagrama: visualizar(): agenda de quem esta logado
    @Transactional(readOnly = true)
    public List<Agendamento> visualizarAgenda(UserSpringSecurity usuarioLogado) {
        Optional<Tutor> tutor = tutorRepository.findByUsuario_Id(usuarioLogado.getId());
        if (tutor.isPresent()) {
            return agendamentoRepository.findByTutor_IdOrderByDataAscHoraAsc(tutor.get().getId());
        }
        Optional<Estudante> estudante = estudanteRepository.findByUsuario_Id(usuarioLogado.getId());
        if (estudante.isPresent()) {
            return agendamentoRepository.findByEstudante_IdOrderByDataAscHoraAsc(estudante.get().getId());
        }
        if (autorizacaoService.isAdmin(usuarioLogado)) {
            return agendamentoRepository.findAllFull();
        }
        return List.of();
    }

    // Diagrama: visualizar(Disciplina): horarios disponiveis daquela disciplina.
    // Sem disciplina, lista os horarios disponiveis de todas.
    @Transactional(readOnly = true)
    public List<Agendamento> visualizarHorariosDisponiveis(String disciplina) {
        List<Agendamento> horarios = disciplina == null || disciplina.isBlank()
                ? agendamentoRepository.findDisponiveis(hoje(), UsuarioStatusEnum.ATIVO)
                : agendamentoRepository.findDisponiveisPorDisciplina(hoje(), UsuarioStatusEnum.ATIVO, disciplina);
        return somenteFuturos(horarios);
    }

    @Transactional(readOnly = true)
    public List<Agendamento> listarDisponiveisPorTutor(Long tutorId) {
        return somenteFuturos(
                agendamentoRepository.findDisponiveisPorTutor(hoje(), UsuarioStatusEnum.ATIVO, tutorId));
    }

    @Transactional(readOnly = true)
    public Agendamento buscarPorId(Long id) {
        return agendamentoRepository.findByIdFull(id)
                .orElseThrow(() -> new ObjectNotFoundException("Agendamento nao encontrado: " + id));
    }

    // Sessoes DISPONIVEIS sao publicas para usuarios logados (ja aparecem na
    // listagem). As demais: so admin, o tutor da sessao ou o estudante que a solicitou.
    @Transactional(readOnly = true)
    public Agendamento buscarPorId(Long id, UserSpringSecurity usuarioLogado) {
        Agendamento agendamento = buscarPorId(id);

        boolean participante = isTutorDoAgendamento(agendamento, usuarioLogado)
                || isEstudanteDoAgendamento(agendamento, usuarioLogado);

        if (!agendamento.isDisponivel()
                && !autorizacaoService.isAdmin(usuarioLogado)
                && !participante) {
            throw new AccessDeniedException("Voce nao tem acesso a este agendamento");
        }
        return agendamento;
    }

    @Transactional(readOnly = true)
    public List<Agendamento> listarTodos() {
        return agendamentoRepository.findAllFull();
    }

    @Transactional(readOnly = true)
    public List<Agendamento> listarAgendamentosPorTutor(Long tutorId) {
        if (!tutorRepository.existsById(tutorId)) {
            throw new ObjectNotFoundException("Tutor nao encontrado: " + tutorId);
        }
        return agendamentoRepository.findByTutor_IdOrderByDataAscHoraAsc(tutorId);
    }

    @Transactional(readOnly = true)
    public List<Agendamento> listarAgendamentosPorEstudante(Long estudanteId) {
        if (!estudanteRepository.existsById(estudanteId)) {
            throw new ObjectNotFoundException("Estudante nao encontrado: " + estudanteId);
        }
        return agendamentoRepository.findByEstudante_IdOrderByDataAscHoraAsc(estudanteId);
    }

    @Transactional
    public void deletar(Long id) {
        if (!agendamentoRepository.existsById(id)) {
            throw new ObjectNotFoundException("Agendamento nao encontrado: " + id);
        }
        try {
            agendamentoRepository.deleteById(id);
        } catch (DataIntegrityViolationException ex) {
            throw new DataBindingViolationException(
                    "Nao e possivel excluir pois ha entidades relacionadas");
        }
    }

    // ── Regras que dependem do banco ou do relogio ───────────────────────────

    private void exigirNoFuturo(Agendamento agendamento) {
        if (agendamento.comecouAte(agora())) {
            throw new IllegalArgumentException("Este horário já passou");
        }
    }

    private void exigirAntesDoDiaDaSessao(Agendamento agendamento) {
        if (!agendamento.getData().isAfter(hoje())) {
            throw new IllegalArgumentException(
                    "O cancelamento só pode ser feito até o dia anterior à tutoria");
        }
    }

    private void exigirAgendaDoTutorLivre(Agendamento agendamento) {
        boolean conflito = agendamentoRepository
                .findByTutor_IdAndData(agendamento.getTutor().getId(), agendamento.getData()).stream()
                .filter(outra -> !outra.equals(agendamento))
                .anyMatch(outra -> outra.ocupaAgenda() && outra.sobrepoe(agendamento));
        if (conflito) {
            throw new IllegalArgumentException("Você já tem uma sessão nesse horário");
        }
    }

    private void exigirAgendaDoEstudanteLivre(Estudante estudante, Agendamento agendamento) {
        boolean conflito = agendamentoRepository
                .findByEstudante_IdAndDataAndStatusIn(estudante.getId(), agendamento.getData(), OCUPAM_O_ESTUDANTE)
                .stream()
                .anyMatch(outra -> outra.sobrepoe(agendamento));
        if (conflito) {
            throw new IllegalArgumentException("Você já tem uma tutoria marcada nesse horário");
        }
    }

    // Horarios de hoje cujo inicio ja passou nao sao mais oferecidos
    private List<Agendamento> somenteFuturos(List<Agendamento> horarios) {
        LocalDateTime agora = agora();
        return horarios.stream().filter(a -> !a.comecouAte(agora)).toList();
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }

    private static AgendamentoStatusEnum converterStatus(String status) {
        try {
            return AgendamentoStatusEnum.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Status inválido. Use CONFIRMADO, CANCELADO ou REALIZADO");
        }
    }

    // Sem modalidade informada, a tutoria e presencial
    private static ModalidadeEnum converterModalidade(String modalidade) {
        if (modalidade == null || modalidade.isBlank()) {
            return ModalidadeEnum.PRESENCIAL;
        }
        try {
            return ModalidadeEnum.valueOf(modalidade.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Modalidade inválida. Use PRESENCIAL ou ONLINE");
        }
    }

    private Agendamento buscarParaAlterar(Long id) {
        return agendamentoRepository.findByIdParaAlterar(id)
                .orElseThrow(() -> new ObjectNotFoundException("Agendamento nao encontrado: " + id));
    }

    private boolean isTutorDoAgendamento(Agendamento agendamento, UserSpringSecurity usuarioLogado) {
        return autorizacaoService.isUsuario(usuarioLogado, agendamento.getTutor().getUsuario().getId());
    }

    private boolean isEstudanteDoAgendamento(Agendamento agendamento, UserSpringSecurity usuarioLogado) {
        return agendamento.getEstudante() != null
                && autorizacaoService.isUsuario(usuarioLogado, agendamento.getEstudante().getUsuario().getId());
    }

    private Tutor resolverTutor(Long tutorId) {
        if (tutorId == null) {
            throw new IllegalArgumentException("tutorId é obrigatório");
        }
        return tutorRepository.findById(tutorId)
                .orElseThrow(() -> new ObjectNotFoundException("Tutor nao encontrado: " + tutorId));
    }
}
