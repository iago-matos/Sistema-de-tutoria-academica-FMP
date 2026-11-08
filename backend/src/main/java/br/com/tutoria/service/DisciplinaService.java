package br.com.tutoria.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.dto.DisciplinaDTO;
import br.com.tutoria.dto.PedidoDisciplinaDTO;
import br.com.tutoria.dto.TutorDisciplinaDTO;
import br.com.tutoria.model.Disciplina;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.TutorDisciplina;
import br.com.tutoria.model.enums.TutorDisciplinaStatusEnum;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.repository.DisciplinaRepository;
import br.com.tutoria.repository.TutorDisciplinaRepository;
import br.com.tutoria.service.exceptions.DataBindingViolationException;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;

// Disciplinas: a lista mantida pela coordenacao e as disciplinas de cada tutor.
//
// - A disciplina escolhida no cadastro nasce ATIVA: a coordenacao a aprova junto
//   com a conta do tutor.
// - Disciplinas pedidas depois nascem PENDENTE e so aparecem na busca dos
//   estudantes quando a coordenacao aprovar (guia "Aprovações").
@Service
public class DisciplinaService {

    private final DisciplinaRepository disciplinaRepository;
    private final TutorDisciplinaRepository tutorDisciplinaRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final Clock clock;

    public DisciplinaService(
            DisciplinaRepository disciplinaRepository,
            TutorDisciplinaRepository tutorDisciplinaRepository,
            AgendamentoRepository agendamentoRepository,
            Clock clock) {
        this.disciplinaRepository = disciplinaRepository;
        this.tutorDisciplinaRepository = tutorDisciplinaRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.clock = clock;
    }

    // "  Lógica   de Programação " e "logica de programacao" sao a mesma disciplina
    public static String chave(String nome) {
        if (nome == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // ── Lista da coordenacao ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DisciplinaDTO> listar() {
        return disciplinaRepository.findAllByOrderByNomeAsc().stream().map(DisciplinaDTO::de).toList();
    }

    @Transactional
    public DisciplinaDTO criar(String nome) {
        String limpo = limparNome(nome);
        exigirNomeLivre(limpo, null);
        return DisciplinaDTO.de(disciplinaRepository.save(new Disciplina(limpo)));
    }

    // Renomear vale para todos: tutores e tutorias ja registradas passam a mostrar o nome novo
    @Transactional
    public DisciplinaDTO renomear(Long id, String nome) {
        Disciplina disciplina = buscar(id);
        String limpo = limparNome(nome);
        exigirNomeLivre(limpo, id);
        disciplina.setNome(limpo);
        return DisciplinaDTO.de(disciplinaRepository.save(disciplina));
    }

    // So exclui o que ninguem usa: apagar uma disciplina em uso sumiria com o historico
    @Transactional
    public void excluir(Long id) {
        Disciplina disciplina = buscar(id);
        if (tutorDisciplinaRepository.existsByDisciplina_Id(id) || agendamentoRepository.existsByDisciplina_Id(id)) {
            throw new DataBindingViolationException(
                    "A disciplina \"" + disciplina.getNome() + "\" já está na lista de tutores ou em tutorias "
                            + "registradas e não pode ser excluída. Se o nome estiver errado, renomeie.");
        }
        disciplinaRepository.delete(disciplina);
    }

    @Transactional(readOnly = true)
    public Disciplina buscar(Long id) {
        return disciplinaRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Disciplina não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<Disciplina> buscarPorNome(String nome) {
        String procurada = chave(nome);
        return disciplinaRepository.findAll().stream()
                .filter(d -> chave(d.getNome()).equals(procurada))
                .findFirst();
    }

    // Usado na migracao dos dados antigos e nos dados de demonstracao
    @Transactional
    public Disciplina buscarOuCriar(String nome) {
        return buscarPorNome(nome).orElseGet(() -> disciplinaRepository.save(new Disciplina(limparNome(nome))));
    }

    // ── Disciplinas de um tutor ──────────────────────────────────────────────

    @Transactional
    public TutorDisciplina vincular(Tutor tutor, Disciplina disciplina, TutorDisciplinaStatusEnum status) {
        TutorDisciplina vinculo = tutorDisciplinaRepository.save(
                new TutorDisciplina(tutor, disciplina, status, LocalDateTime.now(clock)));
        tutor.getVinculosDisciplina().add(vinculo);
        return vinculo;
    }

    @Transactional(readOnly = true)
    public List<TutorDisciplinaDTO> doTutor(Tutor tutor) {
        return tutorDisciplinaRepository.findByTutorId(tutor.getId()).stream().map(TutorDisciplinaDTO::de).toList();
    }

    @Transactional(readOnly = true)
    public List<Disciplina> ativasDoTutor(Long tutorId) {
        return tutorDisciplinaRepository.findByTutorId(tutorId).stream()
                .filter(TutorDisciplina::isAtiva)
                .map(TutorDisciplina::getDisciplina)
                .toList();
    }

    // Disciplinas ativas de varios tutores numa consulta so
    @Transactional(readOnly = true)
    public Map<Long, List<DisciplinaDTO>> ativasPorTutor(Collection<Long> tutorIds) {
        if (tutorIds.isEmpty()) {
            return Map.of();
        }
        return tutorDisciplinaRepository.findByTutoresEStatus(tutorIds, TutorDisciplinaStatusEnum.ATIVA).stream()
                .collect(Collectors.groupingBy(td -> td.getTutor().getId(),
                        Collectors.mapping(td -> DisciplinaDTO.de(td.getDisciplina()), Collectors.toList())));
    }

    // O tutor pede uma disciplina nova; uma recusada pode ser pedida de novo
    @Transactional
    public TutorDisciplinaDTO pedir(Tutor tutor, Long disciplinaId) {
        Disciplina disciplina = buscar(disciplinaId);
        Optional<TutorDisciplina> existente =
                tutorDisciplinaRepository.findByTutor_IdAndDisciplina_Id(tutor.getId(), disciplinaId);

        if (existente.isPresent()) {
            TutorDisciplina vinculo = existente.get();
            switch (vinculo.getStatus()) {
                case ATIVA -> throw new IllegalArgumentException(
                        "\"" + disciplina.getNome() + "\" já está entre as suas disciplinas");
                case PENDENTE -> throw new IllegalArgumentException(
                        "\"" + disciplina.getNome() + "\" já está aguardando a aprovação da coordenação");
                case RECUSADA -> {
                    vinculo.setStatus(TutorDisciplinaStatusEnum.PENDENTE);
                    vinculo.setSolicitadaEm(LocalDateTime.now(clock));
                    return TutorDisciplinaDTO.de(tutorDisciplinaRepository.save(vinculo));
                }
            }
        }
        return TutorDisciplinaDTO.de(vincular(tutor, disciplina, TutorDisciplinaStatusEnum.PENDENTE));
    }

    // Remove da lista: cancela um pedido, apaga uma recusa ou deixa de atender
    // uma disciplina. Tutorias ja marcadas continuam como estao.
    @Transactional
    public void remover(Tutor tutor, Long vinculoId) {
        TutorDisciplina vinculo = tutorDisciplinaRepository.findById(vinculoId)
                .filter(v -> v.getTutor().getId().equals(tutor.getId()))
                .orElseThrow(() -> new ObjectNotFoundException("Disciplina não encontrada na sua lista: " + vinculoId));

        if (vinculo.isAtiva()
                && tutorDisciplinaRepository.countByTutor_IdAndStatus(tutor.getId(), TutorDisciplinaStatusEnum.ATIVA) <= 1) {
            throw new IllegalArgumentException(
                    "Você precisa ter ao menos uma disciplina ativa. Adicione outra antes de remover esta.");
        }
        tutor.getVinculosDisciplina().remove(vinculo);
        tutorDisciplinaRepository.delete(vinculo);
    }

    // ── Pedidos de disciplina (coordenacao) ──────────────────────────────────

    @Transactional(readOnly = true)
    public List<PedidoDisciplinaDTO> pedidosPendentes() {
        return tutorDisciplinaRepository.findByStatusComTutor(TutorDisciplinaStatusEnum.PENDENTE).stream()
                .map(PedidoDisciplinaDTO::de)
                .toList();
    }

    @Transactional
    public TutorDisciplinaDTO aprovar(Long pedidoId) {
        return decidir(pedidoId, TutorDisciplinaStatusEnum.ATIVA);
    }

    @Transactional
    public TutorDisciplinaDTO recusar(Long pedidoId) {
        return decidir(pedidoId, TutorDisciplinaStatusEnum.RECUSADA);
    }

    private TutorDisciplinaDTO decidir(Long pedidoId, TutorDisciplinaStatusEnum decisao) {
        TutorDisciplina pedido = tutorDisciplinaRepository.findById(pedidoId)
                .orElseThrow(() -> new ObjectNotFoundException("Pedido de disciplina não encontrado: " + pedidoId));
        if (pedido.getStatus() != TutorDisciplinaStatusEnum.PENDENTE) {
            throw new IllegalArgumentException("Este pedido já foi decidido");
        }
        pedido.setStatus(decisao);
        return TutorDisciplinaDTO.de(tutorDisciplinaRepository.save(pedido));
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private static String limparNome(String nome) {
        String limpo = nome == null ? "" : nome.trim().replaceAll("\\s+", " ");
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException("Informe o nome da disciplina");
        }
        if (limpo.length() > Disciplina.MAX_NOME) {
            throw new IllegalArgumentException("O nome deve ter no máximo " + Disciplina.MAX_NOME + " caracteres");
        }
        return limpo;
    }

    private void exigirNomeLivre(String nome, Long idAtual) {
        buscarPorNome(nome)
                .filter(existente -> !existente.getId().equals(idAtual))
                .ifPresent(existente -> {
                    throw new IllegalArgumentException("Já existe a disciplina \"" + existente.getNome() + "\"");
                });
    }
}
