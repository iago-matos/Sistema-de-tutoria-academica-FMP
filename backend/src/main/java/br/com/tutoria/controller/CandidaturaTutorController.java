package br.com.tutoria.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.dto.CandidatarSeDTO;
import br.com.tutoria.dto.CandidaturaTutorDTO;
import br.com.tutoria.service.CandidaturaTutorService;
import jakarta.validation.Valid;

// Guia "Candidatar-se" do estudante: pedir para ser tutor com a mesma conta.
// A decisao e da coordenacao, em Aprovacoes > Pedidos de disciplina.
@RestController
@RequestMapping("/estudante/me/candidatura")
public class CandidaturaTutorController {

    private final CandidaturaTutorService candidaturaTutorService;

    public CandidaturaTutorController(CandidaturaTutorService candidaturaTutorService) {
        this.candidaturaTutorService = candidaturaTutorService;
    }

    @GetMapping
    public CandidaturaTutorDTO situacao(@AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return candidaturaTutorService.situacao(usuarioLogado);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CandidaturaTutorDTO candidatar(
            @Valid @RequestBody CandidatarSeDTO dados,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return candidaturaTutorService.candidatar(dados.disciplinaId(), usuarioLogado);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        candidaturaTutorService.cancelar(usuarioLogado);
    }
}
