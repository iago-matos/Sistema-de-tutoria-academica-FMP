package br.com.tutoria.service;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Avaliacao;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.enums.AgendamentoStatusEnum;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.repository.AvaliacaoRepository;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.service.exceptions.DataBindingViolationException;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AvaliacaoService {

    private final AvaliacaoRepository avaliacaoRepository;
    private final TutorRepository tutorRepository;
    private final EstudanteRepository estudanteRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final AutorizacaoService autorizacaoService;
    private final Clock clock;

    public AvaliacaoService(
            AvaliacaoRepository avaliacaoRepository,
            TutorRepository tutorRepository,
            EstudanteRepository estudanteRepository,
            AgendamentoRepository agendamentoRepository,
            AutorizacaoService autorizacaoService,
            Clock clock) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.tutorRepository = tutorRepository;
        this.estudanteRepository = estudanteRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.autorizacaoService = autorizacaoService;
        this.clock = clock;
    }

    // O estudante avalia uma tutoria dele que ja aconteceu, uma unica vez.
    // Autor, tutor, data e hora sao definidos aqui; os do corpo sao ignorados.
    @Transactional
    public Avaliacao salvar(Avaliacao avaliacao, UserSpringSecurity usuarioLogado) {
        Estudante autor = autorizacaoService.estudanteLogado(usuarioLogado);
        validarNota(avaliacao.getNota());

        LocalDateTime agora = LocalDateTime.now(clock);
        Agendamento tutoria = tutoriaAvaliavel(avaliacao.getAgendamento(), autor, agora);

        avaliacao.setId(null);
        avaliacao.setComentario(limparComentario(avaliacao.getComentario()));
        avaliacao.setAgendamento(tutoria);
        avaliacao.setTutor(tutoria.getTutor());
        avaliacao.setEstudante(autor);
        avaliacao.setDataAvaliacao(agora.toLocalDate());
        avaliacao.setHoraAvaliacao(agora.toLocalTime().withNano(0));
        return avaliacaoRepository.save(avaliacao);
    }

    private Agendamento tutoriaAvaliavel(Agendamento ref, Estudante autor, LocalDateTime agora) {
        if (ref == null || ref.getId() == null) {
            throw new IllegalArgumentException("Informe a tutoria avaliada (agendamento.id)");
        }
        // Trava a tutoria ate o fim da transacao: dois envios simultaneos
        // (clique duplo) nao conseguem gravar duas avaliacoes
        Agendamento tutoria = agendamentoRepository.findByIdParaAlterar(ref.getId())
                .orElseThrow(() -> new ObjectNotFoundException("Tutoria nao encontrada: " + ref.getId()));

        if (tutoria.getEstudante() == null || !tutoria.getEstudante().getId().equals(autor.getId())) {
            throw new AccessDeniedException("Você só pode avaliar as suas próprias tutorias");
        }
        boolean aconteceu = (tutoria.getStatus() == AgendamentoStatusEnum.CONFIRMADO
                || tutoria.getStatus() == AgendamentoStatusEnum.REALIZADO)
                && tutoria.comecouAte(agora);
        if (!aconteceu) {
            throw new IllegalArgumentException("Só é possível avaliar uma tutoria depois que ela acontece");
        }
        if (avaliacaoRepository.existsByAgendamento_Id(tutoria.getId())) {
            throw new DataBindingViolationException("Esta tutoria já foi avaliada");
        }
        return tutoria;
    }

    @Transactional(readOnly = true)
    public Avaliacao buscarPorId(Long id) {
        return avaliacaoRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Avaliacao nao encontrada: " + id));
    }

    // Admin, o estudante que avaliou ou o tutor avaliado
    @Transactional(readOnly = true)
    public Avaliacao buscarPorId(Long id, UserSpringSecurity usuarioLogado) {
        Avaliacao avaliacao = buscarPorId(id);

        boolean participante = autorizacaoService.isUsuario(usuarioLogado, avaliacao.getEstudante().getUsuario().getId())
                || autorizacaoService.isUsuario(usuarioLogado, avaliacao.getTutor().getUsuario().getId());

        if (!autorizacaoService.isAdmin(usuarioLogado) && !participante) {
            throw new AccessDeniedException("Voce nao tem acesso a esta avaliacao");
        }
        return avaliacao;
    }

    @Transactional(readOnly = true)
    public List<Avaliacao> listarTodos() {
        return avaliacaoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Avaliacao> listarAvaliacoesPorTutor(Long tutorId) {
        if (!tutorRepository.existsById(tutorId)) {
            throw new ObjectNotFoundException("Tutor nao encontrado: " + tutorId);
        }
        return avaliacaoRepository.findByTutor_Id(tutorId);
    }

    @Transactional(readOnly = true)
    public List<Avaliacao> listarAvaliacoesPorEstudante(Long estudanteId) {
        if (!estudanteRepository.existsById(estudanteId)) {
            throw new ObjectNotFoundException("Estudante nao encontrado: " + estudanteId);
        }
        return avaliacaoRepository.findByEstudante_Id(estudanteId);
    }

    @Transactional
    public Avaliacao atualizar(Long id, Avaliacao dados) {
        Avaliacao existente = avaliacaoRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Avaliacao nao encontrada: " + id));

        // Moderacao: so nota e comentario mudam (comentario vazio remove o
        // texto, ex.: ofensivo). Tutor, estudante, tutoria, data e hora
        // registram quem avaliou o que e quando.
        if (dados.getNota() != null) {
            validarNota(dados.getNota());
            existente.setNota(dados.getNota());
        }
        if (dados.getComentario() != null) {
            existente.setComentario(limparComentario(dados.getComentario()));
        }
        return avaliacaoRepository.save(existente);
    }

    @Transactional
    public void deletar(Long id) {
        if (!avaliacaoRepository.existsById(id)) {
            throw new ObjectNotFoundException("Avaliacao nao encontrada: " + id);
        }
        try {
            avaliacaoRepository.deleteById(id);
        } catch (DataIntegrityViolationException ex) {
            throw new DataBindingViolationException(
                    "Nao e possivel excluir pois ha entidades relacionadas");
        }
    }

    // Opcional: em branco vira nulo; espacos nas pontas sao removidos
    private String limparComentario(String comentario) {
        if (comentario == null || comentario.isBlank()) {
            return null;
        }
        String limpo = comentario.strip();
        if (limpo.length() > Avaliacao.MAX_COMENTARIO) {
            throw new IllegalArgumentException(
                    "O comentário deve ter no máximo " + Avaliacao.MAX_COMENTARIO + " caracteres");
        }
        return limpo;
    }

    private void validarNota(Integer nota) {
        if (nota == null || nota < 1 || nota > 5) {
            throw new IllegalArgumentException("Nota deve ser inteira de 1 a 5");
        }
    }

}
