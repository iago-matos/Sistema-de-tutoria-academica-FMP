package br.com.tutoria.Security;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import br.com.tutoria.service.UsuarioService;
import br.com.tutoria.service.exceptions.AcessoRecusadoException;

// Confere o ID token que o botao "Fazer login com Google" entrega ao frontend.
//
// O token so e aceito se:
// - foi assinado pelo Google (chaves publicas em JWKS_URI) e nao expirou;
// - foi emitido para ESTE sistema (aud = GOOGLE_CLIENT_ID): um token gerado
//   para outro site nao serve aqui;
// - o e-mail foi verificado pelo Google e pertence a conta Google Workspace
//   da instituicao (claim "hd" e dominio do e-mail em GOOGLE_DOMINIOS).
//   Uma conta @gmail.com com o mesmo nome nao passa.
@Component
public class GoogleTokenVerifier {

    static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    static final List<String> EMISSORES = List.of("accounts.google.com", "https://accounts.google.com");

    private static final int TAMANHO_MAXIMO_NOME = 120;

    private final List<String> dominios;
    private final JwtDecoder decoder;

    @Autowired
    public GoogleTokenVerifier(
            @Value("${app.google.client-id:}") String clientId,
            @Value("${app.google.dominios:}") String dominios) {

        this(dominios, clientId == null || clientId.isBlank() ? null : criarDecoder(clientId.trim()));
    }

    // Usado pelos testes, com um decoder que nao acessa o Google
    GoogleTokenVerifier(String dominios, JwtDecoder decoder) {
        this.dominios = lerDominios(dominios);
        this.decoder = decoder;
    }

    // "fmpsc.edu.br, Aluno.fmpsc.edu.br" -> [fmpsc.edu.br, aluno.fmpsc.edu.br]
    public static List<String> lerDominios(String dominios) {
        if (dominios == null) {
            return List.of();
        }
        return Arrays.stream(dominios.split(","))
                .map(UsuarioService::normalizarEmail)
                .filter(dominio -> !dominio.isEmpty())
                .toList();
    }

    public record IdentidadeGoogle(String email, String nome) { }

    public IdentidadeGoogle verificar(String credential) {
        if (decoder == null) {
            throw new AcessoRecusadoException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O login com Google não está configurado no servidor.");
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(credential);
        } catch (JwtException e) {
            throw new AcessoRecusadoException(HttpStatus.UNAUTHORIZED,
                    "Não foi possível validar o login com o Google. Tente novamente.");
        }

        String email = UsuarioService.normalizarEmail(jwt.getClaimAsString("email"));
        if (email == null || email.isEmpty() || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new AcessoRecusadoException(HttpStatus.UNAUTHORIZED,
                    "O Google não confirmou o e-mail desta conta.");
        }

        // "hd" e o dominio principal da organizacao no Google Workspace. Se os alunos
        // usam um dominio secundario (@aluno...), o hd pode ser o principal; por isso
        // os dois sao conferidos contra a mesma lista.
        String hd = UsuarioService.normalizarEmail(jwt.getClaimAsString("hd"));
        String dominioDoEmail = email.substring(email.lastIndexOf('@') + 1);
        if (!dominios.contains(hd) || !dominios.contains(dominioDoEmail)) {
            throw new AcessoRecusadoException(HttpStatus.FORBIDDEN,
                    "Use sua conta institucional (" + descreverDominios() + ") para entrar.");
        }

        return new IdentidadeGoogle(email, nomeDe(jwt, email));
    }

    private String descreverDominios() {
        return String.join(" ou ", dominios.stream().map(dominio -> "@" + dominio).toList());
    }

    private static String nomeDe(Jwt jwt, String email) {
        String nome = jwt.getClaimAsString("name");
        if (nome == null || nome.isBlank()) {
            nome = email.substring(0, email.indexOf('@'));
        }
        nome = nome.trim();
        return nome.length() > TAMANHO_MAXIMO_NOME ? nome.substring(0, TAMANHO_MAXIMO_NOME) : nome;
    }

    private static JwtDecoder criarDecoder(String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        decoder.setJwtValidator(validador(clientId));
        return decoder;
    }

    // Validade, emissor e destinatario do token (a assinatura e conferida pelo decoder)
    static OAuth2TokenValidator<Jwt> validador(String clientId) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                        iss -> iss != null && EMISSORES.contains(iss.toString())),
                new JwtClaimValidator<Object>(JwtClaimNames.AUD,
                        aud -> aud instanceof Collection<?> lista ? lista.contains(clientId) : clientId.equals(aud)));
    }
}
