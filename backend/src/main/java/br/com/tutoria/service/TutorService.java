package br.com.tutoria.service;

import br.com.tutoria.dto.AvaliacaoEstatisticaDTO;
import br.com.tutoria.dto.TutorResumoDTO;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.AvaliacaoRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.repository.UsuarioRepository;
import br.com.tutoria.service.exceptions.DataBindingViolationException;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TutorService {

    private final TutorRepository tutorRepository;
    private final UsuarioRepository usuarioRepository;
    private final AvaliacaoRepository avaliacaoRepository;

    public TutorService(
            TutorRepository tutorRepository,
            UsuarioRepository usuarioRepository,
            AvaliacaoRepository avaliacaoRepository) {
        this.tutorRepository = tutorRepository;
        this.usuarioRepository = usuarioRepository;
        this.avaliacaoRepository = avaliacaoRepository;
    }

    // Lista publica: apenas tutores com conta ativa, com a media das avaliacoes
    @Transactional(readOnly = true)
    public List<TutorResumoDTO> listarResumos() {
        Map<Long, AvaliacaoEstatisticaDTO> estatisticas = avaliacaoRepository.calcularEstatisticasPorTutor()
                .stream()
                .collect(Collectors.toMap(AvaliacaoEstatisticaDTO::tutorId, Function.identity()));

        return tutorRepository.findByUsuarioStatus(UsuarioStatusEnum.ATIVO).stream()
                .map(tutor -> paraResumo(tutor, estatisticas.get(tutor.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TutorResumoDTO buscarResumo(Long id) {
        Tutor tutor = buscarPorId(id);
        AvaliacaoEstatisticaDTO estatistica = avaliacaoRepository.calcularEstatisticasPorTutor().stream()
                .filter(e -> e.tutorId().equals(id))
                .findFirst()
                .orElse(null);
        return paraResumo(tutor, estatistica);
    }

    private static TutorResumoDTO paraResumo(Tutor tutor, AvaliacaoEstatisticaDTO estatistica) {
        return new TutorResumoDTO(
                tutor.getId(),
                tutor.getDisciplina(),
                new TutorResumoDTO.UsuarioNomeDTO(tutor.getUsuario().getId(), tutor.getUsuario().getNome()),
                estatistica != null ? estatistica.media() : null,
                estatistica != null ? estatistica.total() : 0L);
    }

    @Transactional
    public Tutor salvar(Tutor tutor) {
        if (tutor.getUsuario() == null || tutor.getUsuario().getId() == null) {
            throw new IllegalArgumentException("Informe usuario.id no JSON para vincular o tutor.");
        }
        Usuario usuario = usuarioRepository.findById(tutor.getUsuario().getId())
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Usuario nao encontrado: " + tutor.getUsuario().getId()));
        tutor.setUsuario(usuario);
        return tutorRepository.save(tutor);
    }

    @Transactional(readOnly = true)
    public Tutor buscarPorId(Long id) {
        return tutorRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Tutor nao encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<Tutor> listarTodos() {
        return tutorRepository.findAll();
    }

    @Transactional
    public Tutor atualizar(Long id, Tutor dados) {
        Tutor existente = tutorRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Tutor nao encontrado: " + id));

        if (dados.getDisciplina() != null) {
            existente.setDisciplina(dados.getDisciplina());
        }
        if (dados.getUsuario() != null && dados.getUsuario().getId() != null) {
            Usuario u = usuarioRepository.findById(dados.getUsuario().getId())
                    .orElseThrow(() -> new ObjectNotFoundException(
                            "Usuario nao encontrado: " + dados.getUsuario().getId()));
            existente.setUsuario(u);
        }
        return tutorRepository.save(existente);
    }

    @Transactional
    public void deletar(Long id) {
        if (!tutorRepository.existsById(id)) {
            throw new ObjectNotFoundException("Tutor nao encontrado: " + id);
        }
        try {
            tutorRepository.deleteById(id);
        } catch (DataIntegrityViolationException ex) {
            throw new DataBindingViolationException(
                    "Nao e possivel excluir pois ha entidades relacionadas");
        }
    }
}
