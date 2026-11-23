package br.com.tutoria.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.dto.ConversaResumoDTO;
import br.com.tutoria.dto.EnviarMensagemDTO;
import br.com.tutoria.dto.IniciarConversaDTO;
import br.com.tutoria.dto.MensagemDTO;
import br.com.tutoria.service.ConversaService;

// Chat entre tutor e estudante. Todas as rotas sao do usuario logado:
// cada um so ve e escreve nas proprias conversas.
@RestController
@RequestMapping("/conversa")
public class ConversaController {

    private final ConversaService conversaService;

    public ConversaController(ConversaService conversaService) {
        this.conversaService = conversaService;
    }

    // ?perfil=TUTOR|ESTUDANTE: so as conversas daquele papel (quem tem os dois perfis)
    @GetMapping
    public List<ConversaResumoDTO> listar(
            @RequestParam(required = false) String perfil,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return conversaService.listar(usuarioLogado, perfil);
    }

    // Abre a conversa com o tutor (estudante logado) ou com o estudante (tutor logado).
    // Se a dupla ja tem conversa, devolve a mesma.
    @PostMapping
    public ConversaResumoDTO iniciar(
            @RequestBody IniciarConversaDTO dados,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return conversaService.iniciar(dados, usuarioLogado);
    }

    @GetMapping("/nao-lidas")
    public Map<String, Long> naoLidas(@AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return Map.of("total", conversaService.totalNaoLidas(usuarioLogado.getId()));
    }

    @GetMapping("/{id}")
    public ConversaResumoDTO buscar(
            @PathVariable Long id,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return conversaService.buscar(id, usuarioLogado);
    }

    // aposId: so as mensagens mais novas que essa (a tela consulta a cada poucos segundos)
    @GetMapping("/{id}/mensagens")
    public List<MensagemDTO> mensagens(
            @PathVariable Long id,
            @RequestParam(required = false) Long aposId,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return conversaService.mensagens(id, aposId, usuarioLogado);
    }

    @PostMapping("/{id}/mensagens")
    @ResponseStatus(HttpStatus.CREATED)
    public MensagemDTO enviar(
            @PathVariable Long id,
            @RequestBody EnviarMensagemDTO dados,
            @AuthenticationPrincipal UserSpringSecurity usuarioLogado) {
        return conversaService.enviar(id, dados.conteudo(), usuarioLogado);
    }
}
