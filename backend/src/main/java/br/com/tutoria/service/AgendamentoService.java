package br.com.tutoria.service;

import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.service.exceptions.DataBindingViolationException;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;

@Service
public class AgendamentoService {

    // DISPONIVEL: tutor criou a sessão, aguarda solicitação de estudante
    // PENDENTE:   estudante solicitou, aguarda confirmação do tutor
    // CONFIRMADO: tutor aceitou
    // REALIZADO:  sessão concluída
    // CANCELADO:  recusado ou cancelado
    private static final Set<String> STATUS_VALIDOS = Set.of(
            "DISPONIVEL", "PENDENTE", "CONFIRMADO", "REALIZADO", "CANCELADO");

    private final AgendamentoRepository agendamentoRepository;
    private final TutorRepository tutorRepository;
    private final EstudanteRepository estudanteRepository;

    public AgendamentoService(
            AgendamentoRepository agendamentoRepository,
            TutorRepository tutorRepository,
            EstudanteRepository estudanteRepository) {
        this.agendamentoRepository = agendamentoRepository;
        this.tutorRepository = tutorRepository;
        this.estudanteRepository = estudanteRepository;
    }

    // ── Tutor cria uma sessão disponível (sem estudante) ─────────────────────
    @Transactional
    public Agendamento salvar(Agendamento agendamento) {
        if (agendamento.getData() == null) {
            throw new IllegalArgumentException("O campo 'data' é obrigatório");
        }
        if (agendamento.getHora() == null) {
            throw new IllegalArgumentException("O campo 'hora' é obrigatório");
        }

        agendamento.setTutor(resolverTutor(agendamento.getTutor()));
        agendamento.setEstudante(null);
        agendamento.setStatus("DISPONIVEL");

        return agendamentoRepository.save(agendamento);
    }

    // ── Estudante solicita uma sessão disponível ──────────────────────────────
    @Transactional
    public Agendamento solicitar(Long agendamentoId, Long estudanteId) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new ObjectNotFoundException("Agendamento nao encontrado: " + agendamentoId));

        if (!"DISPONIVEL".equals(agendamento.getStatus())) {
            throw new IllegalArgumentException(
                    "Esta sessão não está disponível para solicitação (status: " + agendamento.getStatus() + ")");
        }
        if (agendamento.getEstudante() != null) {
            throw new IllegalArgumentException("Esta sessão já foi solicitada por outro estudante");
        }

        Estudante estudante = estudanteRepository.findById(estudanteId)
                .orElseThrow(() -> new ObjectNotFoundException("Estudante nao encontrado: " + estudanteId));

        agendamento.setEstudante(estudante);
        agendamento.setStatus("PENDENTE");
        agendamentoRepository.save(agendamento);

        // Recarrega com JOIN FETCH para retornar tutor.usuario e estudante.usuario
        return agendamentoRepository.findByIdFull(agendamentoId)
                .orElseThrow(() -> new ObjectNotFoundException("Agendamento nao encontrado: " + agendamentoId));
    }

    @Transactional(readOnly = true)
    public Agendamento buscarPorId(Long id) {
        return agendamentoRepository.findByIdFull(id)
                .orElseThrow(() -> new ObjectNotFoundException("Agendamento nao encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<Agendamento> listarTodos() {
        return agendamentoRepository.findAllFull();
    }

    // ── Sessões disponíveis para estudantes verem ─────────────────────────────
    @Transactional(readOnly = true)
    public List<Agendamento> listarDisponiveis() {
        return agendamentoRepository.findByStatusOrderByDataAscHoraAsc("DISPONIVEL");
    }

    // ── Sessões disponíveis de um tutor específico ────────────────────────────
    @Transactional(readOnly = true)
    public List<Agendamento> listarDisponiveisPorTutor(Long tutorId) {
        return agendamentoRepository.findByTutor_IdAndStatusOrderByDataAscHoraAsc(tutorId, "DISPONIVEL");
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
    public Agendamento atualizar(Long id, Agendamento dados) {
        Agendamento existente = agendamentoRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Agendamento nao encontrado: " + id));

        if (dados.getStatus() != null) {
            validarStatus(dados.getStatus());
            existente.setStatus(dados.getStatus());
        }
        if (dados.getData() != null) {
            existente.setData(dados.getData());
        }
        if (dados.getHora() != null) {
            existente.setHora(dados.getHora());
        }
        if (dados.getObservacoes() != null) {
            existente.setObservacoes(dados.getObservacoes());
        }
        agendamentoRepository.save(existente);

        // Recarrega com JOIN FETCH para retornar tutor.usuario e estudante.usuario
        return agendamentoRepository.findByIdFull(id)
                .orElseThrow(() -> new ObjectNotFoundException("Agendamento nao encontrado: " + id));
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

    private void validarStatus(String status) {
        if (status == null || !STATUS_VALIDOS.contains(status.toUpperCase())) {
            throw new IllegalArgumentException(
                    "Status invalido. Use: DISPONIVEL, PENDENTE, CONFIRMADO, REALIZADO ou CANCELADO");
        }
    }

    private Tutor resolverTutor(Tutor ref) {
        if (ref == null || ref.getId() == null) {
            throw new IllegalArgumentException("tutor.id e obrigatorio");
        }
        return tutorRepository.findById(ref.getId())
                .orElseThrow(() -> new ObjectNotFoundException("Tutor nao encontrado: " + ref.getId()));
    }

}
