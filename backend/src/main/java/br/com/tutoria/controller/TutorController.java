package br.com.tutoria.controller;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.dto.AdicionarDisciplinaDTO;
import br.com.tutoria.dto.TutorDisciplinaDTO;
import br.com.tutoria.dto.TutorResumoDTO;
import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Avaliacao;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.service.AgendamentoService;
import br.com.tutoria.service.AutorizacaoService;
import br.com.tutoria.service.AvaliacaoService;
import br.com.tutoria.service.DisciplinaService;
import br.com.tutoria.service.TutorService;
import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/tutor")
public class TutorController {

    private final TutorService tutorService;
    private final AgendamentoService agendamentoService;
    private final AvaliacaoService avaliacaoService;
    private final AutorizacaoService autorizacaoService;
    private final DisciplinaService disciplinaService;

    public TutorController(
            TutorService tutorService,
            AgendamentoService agendamentoService,
            AvaliacaoService avaliacaoService,
            AutorizacaoService autorizacaoService,
            DisciplinaService disciplinaService) {
        this.tutorService = tutorService;
        this.agendamentoService = agendamentoService;
        this.avaliacaoService = avaliacaoService;
        this.autorizacaoService = autorizacaoService;
        this.disciplinaService = disciplinaService;
    }

    // Perfis de tutor nascem no cadastro; criar direto e so para administradores
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Tutor salvar(@RequestBody Tutor tutor) {
        return tutorService.salvar(tutor);
    }

    // Qualquer usuario logado: tutores ativos, sem e-mail, com media das avaliacoes
    @GetMapping
    public List<TutorResumoDTO> listarTodos() {
        return tutorService.listarResumos();
    }

    @GetMapping("/{id}")
    public TutorResumoDTO buscarPorId(@PathVariable Long id) {
        return tutorService.buscarResumo(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Tutor atualizar(@PathVariable Long id, @RequestBody Tutor tutor) {
        return tutorService.atualizar(id, tutor);
    }

    // Nao ha DELETE: contas sao inativadas em PUT /usuario/{id}/inativar,
    // para guardar o historico (sessoes, avaliacoes e mensagens)

    // ── Guia "Disciplinas" do tutor logado ───────────────────────────────────

    @GetMapping("/me/disciplinas")
    public List<TutorDisciplinaDTO> minhasDisciplinas(@AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return disciplinaService.doTutor(autorizacaoService.tutorLogado(usuarioLogado));
    }

    // A disciplina pedida fica aguardando a aprovacao da coordenacao
    @PostMapping("/me/disciplinas")
    @ResponseStatus(HttpStatus.CREATED)
    public TutorDisciplinaDTO pedirDisciplina(
            @Valid @RequestBody AdicionarDisciplinaDTO dados,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return disciplinaService.pedir(autorizacaoService.tutorLogado(usuarioLogado), dados.disciplinaId());
    }

    @DeleteMapping("/me/disciplinas/{vinculoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerDisciplina(
            @PathVariable Long vinculoId,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        disciplinaService.remover(autorizacaoService.tutorLogado(usuarioLogado), vinculoId);
    }

    @GetMapping("/{id}/agendamentos")
    public List<Agendamento> listarAgendamentos(
            @PathVariable Long id,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        autorizacaoService.exigirAcessoAoTutor(id, usuarioLogado);
        return agendamentoService.listarAgendamentosPorTutor(id);
    }

    @GetMapping("/{id}/avaliacoes")
    public List<Avaliacao> listarAvaliacoes(
            @PathVariable Long id,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        autorizacaoService.exigirAcessoAoTutor(id, usuarioLogado);
        return avaliacaoService.listarAvaliacoesPorTutor(id);
    }
}
