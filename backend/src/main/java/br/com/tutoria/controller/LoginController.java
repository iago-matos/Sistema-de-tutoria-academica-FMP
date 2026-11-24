package br.com.tutoria.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.tutoria.Security.GoogleTokenVerifier;
import br.com.tutoria.dto.EsqueciSenhaDTO;
import br.com.tutoria.dto.LoginConfigDTO;
import br.com.tutoria.dto.LoginGoogleDTO;
import br.com.tutoria.dto.LoginGoogleRespostaDTO;
import br.com.tutoria.dto.RedefinirSenhaDTO;
import br.com.tutoria.service.LoginGoogleService;
import br.com.tutoria.service.RedefinicaoSenhaService;
import br.com.tutoria.service.UsuarioService;
import jakarta.validation.Valid;

// O POST /login (e-mail e senha) e tratado pelo JWTAuthenticationFilter.
// Aqui ficam as rotas publicas do login com Google e da recuperacao de senha.
@RestController
@RequestMapping("/login")
public class LoginController {

    private final LoginGoogleService loginGoogleService;
    private final UsuarioService usuarioService;
    private final RedefinicaoSenhaService redefinicaoSenhaService;
    private final String googleClientId;
    private final List<String> dominios;

    public LoginController(
            LoginGoogleService loginGoogleService,
            UsuarioService usuarioService,
            RedefinicaoSenhaService redefinicaoSenhaService,
            @Value("${app.google.client-id:}") String googleClientId,
            @Value("${app.google.dominios:}") String dominios) {

        this.loginGoogleService = loginGoogleService;
        this.usuarioService = usuarioService;
        this.redefinicaoSenhaService = redefinicaoSenhaService;
        this.googleClientId = googleClientId.trim();
        this.dominios = GoogleTokenVerifier.lerDominios(dominios);
    }

    @GetMapping("/config")
    public LoginConfigDTO config() {
        return new LoginConfigDTO(googleClientId, dominios, usuarioService.isCadastroComSenhaHabilitado(),
                redefinicaoSenhaService.isEnvioPorEmailHabilitado());
    }

    @PostMapping("/google")
    public ResponseEntity<LoginGoogleRespostaDTO> entrarComGoogle(@Valid @RequestBody LoginGoogleDTO dados) {
        LoginGoogleService.Resultado resultado = loginGoogleService.entrar(dados);

        return switch (resultado.situacao()) {
            case AUTENTICADO -> ResponseEntity.ok()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + resultado.token())
                    .body(resposta(resultado, null));
            case CADASTRO_NECESSARIO -> ResponseEntity.ok(resposta(resultado,
                    "Primeiro acesso: escolha o seu perfil para concluir o cadastro."));
            case AGUARDANDO_APROVACAO -> ResponseEntity.status(HttpStatus.ACCEPTED).body(resposta(resultado,
                    "Cadastro de tutor enviado! Você poderá entrar assim que um administrador aprovar."));
        };
    }

    // RF02 - "Esqueci minha senha". Sempre 202 com a mesma mensagem, exista ou
    // nao a conta: a tela nao revela quais e-mails estao cadastrados.
    @PostMapping("/esqueci-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> esqueciSenha(@Valid @RequestBody EsqueciSenhaDTO dados) {
        redefinicaoSenhaService.solicitarPorEmail(dados.email());
        return Map.of("message", "Se houver uma conta ativa com este e-mail, enviamos um link para "
                + "criar uma nova senha. Confira a caixa de entrada e o spam.");
    }

    // Nova senha a partir do link recebido por e-mail ou entregue pela coordenacao
    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@Valid @RequestBody RedefinirSenhaDTO dados) {
        redefinicaoSenhaService.redefinir(dados.token(), dados.novaSenha());
    }

    private static LoginGoogleRespostaDTO resposta(LoginGoogleService.Resultado resultado, String message) {
        return new LoginGoogleRespostaDTO(resultado.situacao(), resultado.nome(), resultado.email(), message);
    }
}
