package br.com.tutoria.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.dto.ConversaResumoDTO;
import br.com.tutoria.dto.IniciarConversaDTO;
import br.com.tutoria.dto.MensagemDTO;
import br.com.tutoria.model.Conversa;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Mensagem;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.repository.ConversaRepository;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.MensagemRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;

// Chat entre tutor e estudante. As regras da conversa (quem participa, quem
// pode escrever, conversa encerrada) ficam em Conversa e Mensagem; aqui ficam
// as que dependem do banco e de quem esta logado.
//
// Privacidade: so a dupla le a conversa. Nem a coordenacao (admin) tem acesso.
@Service
public class ConversaService {

    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;
    private final TutorRepository tutorRepository;
    private final EstudanteRepository estudanteRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final AutorizacaoService autorizacaoService;
    private final Clock clock;

    public ConversaService(
            ConversaRepository conversaRepository,
            MensagemRepository mensagemRepository,
            TutorRepository tutorRepository,
            EstudanteRepository estudanteRepository,
            AgendamentoRepository agendamentoRepository,
            AutorizacaoService autorizacaoService,
            Clock clock) {
        this.conversaRepository = conversaRepository;
        this.mensagemRepository = mensagemRepository;
        this.tutorRepository = tutorRepository;
        this.estudanteRepository = estudanteRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.autorizacaoService = autorizacaoService;
        this.clock = clock;
    }

    // Abre a conversa da dupla, ou devolve a que ja existe (uma por dupla).
    // - Estudante: com qualquer tutor ativo (por exemplo, tirar uma duvida antes de agendar).
    // - Tutor: so com estudante que ja solicitou tutoria com ele, para nao
    //   receber mensagens de quem nunca procurou o tutor.
    @Transactional
    public ConversaResumoDTO iniciar(IniciarConversaDTO dados, UserSpringSecurity usuarioLogado) {
        Tutor tutor;
        Estudante estudante;

        if (dados.tutorId() != null) {
            estudante = autorizacaoService.estudanteLogado(usuarioLogado);
            tutor = tutorRepository.findById(dados.tutorId())
                    .orElseThrow(() -> new ObjectNotFoundException("Tutor não encontrado: " + dados.tutorId()));
        } else if (dados.estudanteId() != null) {
            tutor = autorizacaoService.tutorLogado(usuarioLogado);
            estudante = estudanteRepository.findById(dados.estudanteId())
                    .orElseThrow(() -> new ObjectNotFoundException("Estudante não encontrado: " + dados.estudanteId()));
            if (!agendamentoRepository.existsByTutor_IdAndEstudante_Id(tutor.getId(), estudante.getId())) {
                throw new AccessDeniedException(
                        "Você só pode iniciar conversa com estudantes que solicitaram tutoria com você");
            }
        } else {
            throw new IllegalArgumentException("Informe o tutor (tutorId) ou o estudante (estudanteId) da conversa");
        }

        Optional<Conversa> existente = conversaRepository.findByTutor_IdAndEstudante_Id(tutor.getId(), estudante.getId());
        Conversa conversa = existente.orElseGet(() ->
                conversaRepository.save(Conversa.iniciar(tutor, estudante, LocalDateTime.now(clock))));

        return resumo(conversa, usuarioLogado.getId(), naoLidas(conversa, usuarioLogado.getId()));
    }

    @Transactional(readOnly = true)
    public List<ConversaResumoDTO> listar(UserSpringSecurity usuarioLogado) {
        Long usuarioId = usuarioLogado.getId();
        List<Conversa> conversas = conversaRepository.findDoUsuario(usuarioId);
        if (conversas.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> naoLidasPorConversa = new HashMap<>();
        List<Long> ids = conversas.stream().map(Conversa::getId).toList();
        for (Object[] linha : mensagemRepository.contarNaoLidasPorConversa(ids, usuarioId)) {
            naoLidasPorConversa.put((Long) linha[0], (Long) linha[1]);
        }

        return conversas.stream()
                .map(c -> resumo(c, usuarioId, naoLidasPorConversa.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversaResumoDTO buscar(Long conversaId, UserSpringSecurity usuarioLogado) {
        Conversa conversa = conversaDoParticipante(conversaId, usuarioLogado);
        return resumo(conversa, usuarioLogado.getId(), naoLidas(conversa, usuarioLogado.getId()));
    }

    // Historico da conversa. Abrir a conversa marca como lidas as mensagens
    // recebidas. aposId (opcional) traz so as mensagens depois dessa.
    @Transactional
    public List<MensagemDTO> mensagens(Long conversaId, Long aposId, UserSpringSecurity usuarioLogado) {
        Conversa conversa = conversaDoParticipante(conversaId, usuarioLogado);
        Usuario leitor = participante(conversa, usuarioLogado);
        LocalDateTime agora = LocalDateTime.now(clock);

        mensagemRepository.findNaoLidas(conversaId, leitor.getId())
                .forEach(mensagem -> mensagem.marcarComoLida(leitor, agora));

        return mensagemRepository.findDaConversa(conversaId, aposId == null ? 0L : aposId).stream()
                .map(mensagem -> MensagemDTO.de(mensagem, leitor.getId()))
                .toList();
    }

    @Transactional
    public MensagemDTO enviar(Long conversaId, String texto, UserSpringSecurity usuarioLogado) {
        Conversa conversa = conversaDoParticipante(conversaId, usuarioLogado);
        Usuario remetente = participante(conversa, usuarioLogado);

        Mensagem mensagem = conversa.enviar(remetente, texto, LocalDateTime.now(clock));
        mensagemRepository.save(mensagem);
        return MensagemDTO.de(mensagem, remetente.getId());
    }

    // Total de mensagens nao lidas em todas as conversas (menu e sino)
    @Transactional(readOnly = true)
    public long totalNaoLidas(Long usuarioId) {
        return mensagemRepository.contarNaoLidasDoUsuario(usuarioId);
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    // 404 se a conversa nao existe; 403 se quem esta logado nao e da dupla
    private Conversa conversaDoParticipante(Long conversaId, UserSpringSecurity usuarioLogado) {
        Conversa conversa = conversaRepository.findByIdFull(conversaId)
                .orElseThrow(() -> new ObjectNotFoundException("Conversa não encontrada: " + conversaId));
        participante(conversa, usuarioLogado);
        return conversa;
    }

    // O Usuario (tutor ou estudante) da conversa que corresponde a quem esta logado
    private static Usuario participante(Conversa conversa, UserSpringSecurity usuarioLogado) {
        Usuario usuarioTutor = conversa.getTutor().getUsuario();
        Usuario usuarioEstudante = conversa.getEstudante().getUsuario();
        if (usuarioTutor.getId().equals(usuarioLogado.getId())) {
            return usuarioTutor;
        }
        if (usuarioEstudante.getId().equals(usuarioLogado.getId())) {
            return usuarioEstudante;
        }
        throw new AccessDeniedException("Você não participa desta conversa");
    }

    private long naoLidas(Conversa conversa, Long usuarioId) {
        return mensagemRepository.findNaoLidas(conversa.getId(), usuarioId).size();
    }

    private ConversaResumoDTO resumo(Conversa conversa, Long usuarioId, long naoLidas) {
        Usuario usuarioTutor = conversa.getTutor().getUsuario();
        boolean souTutor = usuarioTutor.getId().equals(usuarioId);
        Usuario interlocutor = conversa.interlocutorDe(souTutor ? usuarioTutor : conversa.getEstudante().getUsuario());

        Optional<Mensagem> ultima = conversa.getId() == null
                ? Optional.empty()
                : mensagemRepository.findFirstByConversa_IdOrderByIdDesc(conversa.getId());

        return new ConversaResumoDTO(
                conversa.getId(),
                conversa.getTutor().getId(),
                conversa.getEstudante().getId(),
                interlocutor.getNome(),
                souTutor ? "ESTUDANTE" : "TUTOR",
                ultima.map(Mensagem::getConteudo).orElse(null),
                ultima.map(m -> m.getRemetente().getId().equals(usuarioId)).orElse(false),
                conversa.getUltimaMensagemEm(),
                naoLidas,
                conversa.isAberta());
    }
}
