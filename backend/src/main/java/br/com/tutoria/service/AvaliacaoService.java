package br.com.tutoria.service;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.model.Avaliacao;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.repository.AvaliacaoRepository;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.service.exceptions.DataBindingViolationException;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AvaliacaoService {

    private final AvaliacaoRepository avaliacaoRepository;
    private final TutorRepository tutorRepository;
    private final EstudanteRepository estudanteRepository;
    private final AutorizacaoService autorizacaoService;

    public AvaliacaoService(
            AvaliacaoRepository avaliacaoRepository,
            TutorRepository tutorRepository,
            EstudanteRepository estudanteRepository,
            AutorizacaoService autorizacaoService) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.tutorRepository = tutorRepository;
        this.estudanteRepository = estudanteRepository;
        this.autorizacaoService = autorizacaoService;
    }

    // O autor e sempre o estudante logado: o campo "estudante" do corpo e ignorado
    @Transactional
    public Avaliacao salvar(Avaliacao avaliacao, UserSpringSecurity usuarioLogado) {
        Estudante autor = autorizacaoService.estudanteLogado(usuarioLogado);
        validarNota(avaliacao.getNota());
        avaliacao.setTutor(resolverTutor(avaliacao.getTutor()));
        avaliacao.setEstudante(autor);
        return avaliacaoRepository.save(avaliacao);
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

        if (dados.getDataAvaliacao() != null) {
            existente.setDataAvaliacao(dados.getDataAvaliacao());
        }
        if (dados.getHoraAvaliacao() != null) {
            existente.setHoraAvaliacao(dados.getHoraAvaliacao());
        }
        if (dados.getNota() != null) {
            validarNota(dados.getNota());
            existente.setNota(dados.getNota());
        }
        if (dados.getTutor() != null && dados.getTutor().getId() != null) {
            existente.setTutor(resolverTutor(dados.getTutor()));
        }
        if (dados.getEstudante() != null && dados.getEstudante().getId() != null) {
            existente.setEstudante(resolverEstudante(dados.getEstudante()));
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

    private void validarNota(Integer nota) {
        if (nota == null || nota < 1 || nota > 5) {
            throw new IllegalArgumentException("Nota deve ser inteira de 1 a 5");
        }
    }

    private Tutor resolverTutor(Tutor ref) {
        if (ref == null || ref.getId() == null) {
            throw new IllegalArgumentException("tutor.id e obrigatorio");
        }
        return tutorRepository.findById(ref.getId())
                .orElseThrow(() -> new ObjectNotFoundException("Tutor nao encontrado: " + ref.getId()));
    }

    private Estudante resolverEstudante(Estudante ref) {
        if (ref == null || ref.getId() == null) {
            throw new IllegalArgumentException("estudante.id e obrigatorio");
        }
        return estudanteRepository.findById(ref.getId())
                .orElseThrow(() -> new ObjectNotFoundException("Estudante nao encontrado: " + ref.getId()));
    }
}
