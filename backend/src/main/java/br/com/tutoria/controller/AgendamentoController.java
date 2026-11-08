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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.dto.AtualizarAgendamentoDTO;
import br.com.tutoria.dto.LinkReuniaoDTO;
import br.com.tutoria.dto.NovoHorarioDTO;
import br.com.tutoria.dto.SolicitarAgendamentoDTO;
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

    // Tutor abre um horário de 1h na própria agenda
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Agendamento disponibilizar(
            @RequestBody NovoHorarioDTO dados,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return agendamentoService.disponibilizar(dados, usuarioLogado);
    }

    // Estudante solicita um horário disponível, escolhendo PRESENCIAL ou ONLINE
    // e a disciplina ({"modalidade": "ONLINE", "disciplinaId": 3}). O estudante é
    // identificado pelo token (um "estudanteId" enviado é ignorado). Sem corpo, a
    // tutoria é presencial e, se o tutor atende uma disciplina só, é dela.
    @PutMapping("/{id}/solicitar")
    public Agendamento solicitar(
            @PathVariable Long id,
            @RequestBody(required = false) SolicitarAgendamentoDTO dados,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return agendamentoService.solicitar(id,
                dados != null ? dados.modalidade() : null,
                dados != null ? dados.disciplinaId() : null,
                usuarioLogado);
    }

    // Link da videoconferência: tutor e admin sempre; o estudante, só depois
    // que o tutor confirmar a tutoria online
    @GetMapping("/{id}/link-reuniao")
    public LinkReuniaoDTO linkReuniao(
            @PathVariable Long id,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return agendamentoService.linkReuniao(id, usuarioLogado);
    }

    // Tutor/admin: sessão CANCELADA. Estudante: desiste e o horário volta a ficar livre.
    @PutMapping("/{id}/cancelar")
    public Agendamento cancelar(
            @PathVariable Long id,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return agendamentoService.cancelar(id, usuarioLogado);
    }

    // Agenda de quem está logado (tutor, estudante ou, para admin, todas)
    @GetMapping("/minha-agenda")
    public List<Agendamento> minhaAgenda(@AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return agendamentoService.visualizarAgenda(usuarioLogado);
    }

    // Horários disponíveis (futuros); ?disciplina=Cálculo filtra pela disciplina
    @GetMapping("/disponiveis")
    public List<Agendamento> listarDisponiveis(@RequestParam(required = false) String disciplina) {
        return agendamentoService.visualizarHorariosDisponiveis(disciplina);
    }

    // Horários disponíveis de um tutor específico
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

    // Tutor confirma, recusa (CANCELADO), conclui, remarca ou edita observações
    @PutMapping("/{id}")
    public Agendamento atualizar(
            @PathVariable Long id,
            @RequestBody AtualizarAgendamentoDTO dados,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return agendamentoService.atualizar(id, dados, usuarioLogado);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deletar(@PathVariable Long id) {
        agendamentoService.deletar(id);
    }
}
