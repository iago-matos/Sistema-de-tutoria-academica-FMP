package br.com.tutoria.controller;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.model.Avaliacao;
import br.com.tutoria.service.AvaliacaoService;
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
@RequestMapping("/avaliacao")
public class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    public AvaliacaoController(AvaliacaoService avaliacaoService) {
        this.avaliacaoService = avaliacaoService;
    }

    // Apenas estudantes; a avaliacao e registrada em nome de quem esta logado
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Avaliacao salvar(
            @RequestBody Avaliacao avaliacao,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return avaliacaoService.salvar(avaliacao, usuarioLogado);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Avaliacao> listarTodos() {
        return avaliacaoService.listarTodos();
    }

    @GetMapping("/{id}")
    public Avaliacao buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return avaliacaoService.buscarPorId(id, usuarioLogado);
    }

    // Editar e excluir avaliacoes e moderacao: so administradores
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Avaliacao atualizar(@PathVariable Long id, @RequestBody Avaliacao avaliacao) {
        return avaliacaoService.atualizar(id, avaliacao);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deletar(@PathVariable Long id) {
        avaliacaoService.deletar(id);
    }
}
