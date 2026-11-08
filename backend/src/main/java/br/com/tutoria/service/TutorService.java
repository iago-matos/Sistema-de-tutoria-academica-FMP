package br.com.tutoria.service;

import br.com.tutoria.dto.AvaliacaoEstatisticaDTO;
import br.com.tutoria.dto.DisciplinaDTO;
import br.com.tutoria.dto.TutorResumoDTO;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.AvaliacaoRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.repository.UsuarioRepository;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;

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
    private final DisciplinaService disciplinaService;

    public TutorService(
            TutorRepository tutorRepository,
            UsuarioRepository usuarioRepository,
            AvaliacaoRepository avaliacaoRepository,
            DisciplinaService disciplinaService) {
        this.tutorRepository = tutorRepository;
        this.usuarioRepository = usuarioRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.disciplinaService = disciplinaService;
    }

    // Lista publica: apenas tutores com conta ativa, com a media das avaliacoes
    // e as disciplinas aprovadas
    @Transactional(readOnly = true)
    public List<TutorResumoDTO> listarResumos() {
        Map<Long, AvaliacaoEstatisticaDTO> estatisticas = avaliacaoRepository.calcularEstatisticasPorTutor()
                .stream()
                .collect(Collectors.toMap(AvaliacaoEstatisticaDTO::tutorId, Function.identity()));

        List<Tutor> tutores = tutorRepository.findByUsuarioStatus(UsuarioStatusEnum.ATIVO);
        Map<Long, List<DisciplinaDTO>> disciplinas =
                disciplinaService.ativasPorTutor(tutores.stream().map(Tutor::getId).toList());

        return tutores.stream()
                .map(tutor -> paraResumo(tutor, disciplinas.getOrDefault(tutor.getId(), List.of()),
                        estatisticas.get(tutor.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TutorResumoDTO buscarResumo(Long id) {
        Tutor tutor = buscarPorId(id);
        AvaliacaoEstatisticaDTO estatistica = avaliacaoRepository.calcularEstatisticasPorTutor().stream()
                .filter(e -> e.tutorId().equals(id))
                .findFirst()
                .orElse(null);
        List<DisciplinaDTO> disciplinas = disciplinaService.ativasDoTutor(id).stream().map(DisciplinaDTO::de).toList();
        return paraResumo(tutor, disciplinas, estatistica);
    }

    private static TutorResumoDTO paraResumo(Tutor tutor, List<DisciplinaDTO> disciplinas,
            AvaliacaoEstatisticaDTO estatistica) {
        return new TutorResumoDTO(
                tutor.getId(),
                disciplinas,
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

        // As disciplinas mudam pela guia "Disciplinas" (tutor) e "Aprovações" (coordenacao)
        if (dados.getUsuario() != null && dados.getUsuario().getId() != null) {
            Usuario u = usuarioRepository.findById(dados.getUsuario().getId())
                    .orElseThrow(() -> new ObjectNotFoundException(
                            "Usuario nao encontrado: " + dados.getUsuario().getId()));
            existente.setUsuario(u);
        }
        return tutorRepository.save(existente);
    }

}
