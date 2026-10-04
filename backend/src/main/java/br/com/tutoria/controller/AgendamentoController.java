package br.com.tutoria.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.tutoria.model.Agendamento;
import br.com.tutoria.service.AgendamentoService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/agendamento")
public class AgendamentoController {

    private final AgendamentoService agendamentoService;

    public AgendamentoController(AgendamentoService agendamentoService) {
        this.agendamentoService = agendamentoService;
    }

    // Tutor cria sessão disponível
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Agendamento salvar(@Valid @RequestBody Agendamento agendamento) {
        return agendamentoService.salvar(agendamento);
    }

    // Estudante solicita uma sessão disponível
    // Body: { "estudanteId": 3 }
    @PutMapping("/{id}/solicitar")
    public Agendamento solicitar(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long estudanteId = body.get("estudanteId");
        if (estudanteId == null) {
            throw new IllegalArgumentException("estudanteId é obrigatório no body");
        }
        return agendamentoService.solicitar(id, estudanteId);
    }

    // Lista todas as sessões disponíveis (para o estudante ver)
    @GetMapping("/disponiveis")
    public List<Agendamento> listarDisponiveis() {
        return agendamentoService.listarDisponiveis();
    }

    // Lista sessões disponíveis de um tutor específico
    @GetMapping("/disponiveis/tutor/{tutorId}")
    public List<Agendamento> listarDisponiveisPorTutor(@PathVariable Long tutorId) {
        return agendamentoService.listarDisponiveisPorTutor(tutorId);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Agendamento> listarTodos() {
        return agendamentoService.listarTodos();
    }

    @GetMapping("/{id}")
    public Agendamento buscarPorId(@PathVariable Long id) {
        return agendamentoService.buscarPorId(id);
    }

    @GetMapping("/tutor/{id}")
    public List<Agendamento> buscarPorTutor(@PathVariable Long id) {
        return agendamentoService.listarAgendamentosPorTutor(id);
    }

    @GetMapping("/estudante/{id}")
    public List<Agendamento> buscarPorEstudante(@PathVariable Long id) {
        return agendamentoService.listarAgendamentosPorEstudante(id);
    }

    // Tutor confirma/recusa ou atualiza status
    @PutMapping("/{id}")
    public Agendamento atualizar(@PathVariable Long id, @RequestBody Agendamento agendamento) {
        return agendamentoService.atualizar(id, agendamento);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deletar(@PathVariable Long id) {
        agendamentoService.deletar(id);
    }
}
