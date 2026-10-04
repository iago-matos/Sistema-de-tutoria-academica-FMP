package br.com.tutoria.service;

import br.com.tutoria.model.Avaliacao;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.repository.AvaliacaoRepository;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.service.exceptions.DataBindingViolationException;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AvaliacaoService {

    private final AvaliacaoRepository avaliacaoRepository;
    private final TutorRepository tutorRepository;
    private final EstudanteRepository estudanteRepository;

    public AvaliacaoService(
            AvaliacaoRepository avaliacaoRepository,
            TutorRepository tutorRepository,
            EstudanteRepository estudanteRepository) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.tutorRepository = tutorRepository;
        this.estudanteRepository = estudanteRepository;
    }

    @Transactional
    public Avaliacao salvar(Avaliacao avaliacao) {
        validarNota(avaliacao.getNota());
        avaliacao.setTutor(resolverTutor(avaliacao.getTutor()));
        avaliacao.setEstudante(resolverEstudante(avaliacao.getEstudante()));
        return avaliacaoRepository.save(avaliacao);
    }

    @Transactional(readOnly = true)
    public Avaliacao buscarPorId(Long id) {
        return avaliacaoRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Avaliacao nao encontrada: " + id));
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
