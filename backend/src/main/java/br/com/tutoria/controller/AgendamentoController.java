package br.com.tutoria.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.model.Agendamento;
import br.com.tutoria.service.AgendamentoService;
import br.com.tutoria.service.AutorizacaoService;

@RestController
@RequestMapping("/agendamento")
public class AgendamentoController {

    private final AgendamentoService agendamentoService;
    private final AutorizacaoService autorizacaoService;

    public AgendamentoController(AgendamentoService agendamentoService, AutorizacaoService autorizacaoService) {
        this.agendamentoService = agendamentoService;
        this.autorizacaoService = autorizacaoService;
    }

    // Tutor cria sessão disponível na própria agenda (status e estudante são
    // definidos pelo service, por isso o corpo não passa por @Valid)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Agendamento salvar(
            @RequestBody Agendamento agendamento,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return agendamentoService.salvar(agendamento, usuarioLogado);
    }

    // Estudante solicita uma sessão disponível. Não há corpo: o estudante é
    // identificado pelo token (um "estudanteId" enviado é ignorado).
    @PutMapping("/{id}/solicitar")
    public Agendamento solicitar(
            @PathVariable Long id,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return agendamentoService.solicitar(id, usuarioLogado);
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
    public Agendamento buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return agendamentoService.buscarPorId(id, usuarioLogado);
    }

    @GetMapping("/tutor/{id}")
    public List<Agendamento> buscarPorTutor(
            @PathVariable Long id,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        autorizacaoService.exigirAcessoAoTutor(id, usuarioLogado);
        return agendamentoService.listarAgendamentosPorTutor(id);
    }

    @GetMapping("/estudante/{id}")
    public List<Agendamento> buscarPorEstudante(
            @PathVariable Long id,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        autorizacaoService.exigirAcessoAoEstudante(id, usuarioLogado);
        return agendamentoService.listarAgendamentosPorEstudante(id);
    }

    // Tutor confirma/recusa ou atualiza a própria sessão
    @PutMapping("/{id}")
    public Agendamento atualizar(
            @PathVariable Long id,
            @RequestBody Agendamento agendamento,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return agendamentoService.atualizar(id, agendamento, usuarioLogado);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deletar(@PathVariable Long id) {
        agendamentoService.deletar(id);
    }
}
