package br.com.tutoria.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.tutoria.Security.GoogleTokenVerifier;
import br.com.tutoria.dto.LoginConfigDTO;
import br.com.tutoria.dto.LoginGoogleDTO;
import br.com.tutoria.dto.LoginGoogleRespostaDTO;
import br.com.tutoria.service.LoginGoogleService;
import br.com.tutoria.service.UsuarioService;
import jakarta.validation.Valid;

// O POST /login (e-mail e senha) e tratado pelo JWTAuthenticationFilter.
// Aqui ficam as rotas publicas do login com Google.
@RestController
@RequestMapping("/login")
public class LoginController {

    private final LoginGoogleService loginGoogleService;
    private final UsuarioService usuarioService;
    private final String googleClientId;
    private final List<String> dominios;

    public LoginController(
            LoginGoogleService loginGoogleService,
            UsuarioService usuarioService,
            @Value("${app.google.client-id:}") String googleClientId,
            @Value("${app.google.dominios:}") String dominios) {

        this.loginGoogleService = loginGoogleService;
        this.usuarioService = usuarioService;
        this.googleClientId = googleClientId.trim();
        this.dominios = GoogleTokenVerifier.lerDominios(dominios);
    }

    @GetMapping("/config")
    public LoginConfigDTO config() {
        return new LoginConfigDTO(googleClientId, dominios, usuarioService.isCadastroComSenhaHabilitado());
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

    private static LoginGoogleRespostaDTO resposta(LoginGoogleService.Resultado resultado, String message) {
        return new LoginGoogleRespostaDTO(resultado.situacao(), resultado.nome(), resultado.email(), message);
    }
}
