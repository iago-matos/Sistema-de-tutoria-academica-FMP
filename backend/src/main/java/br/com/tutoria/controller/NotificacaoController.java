package br.com.tutoria.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.dto.NotificacaoDTO;
import br.com.tutoria.service.NotificacaoService;

// Avisos do sino, calculados para quem esta logado (estudante, tutor ou coordenacao)
@RestController
@RequestMapping("/notificacao")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    public NotificacaoController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @GetMapping
    public List<NotificacaoDTO> listar(@AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return notificacaoService.listar(usuarioLogado);
    }
}
