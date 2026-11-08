package br.com.tutoria.Security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import br.com.tutoria.Security.GoogleTokenVerifier.IdentidadeGoogle;
import br.com.tutoria.service.exceptions.AcessoRecusadoException;

// Regras do token do Google, sem acessar a rede: o decoder e substituido
// por um que devolve o token montado em cada teste.
class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "client-id-dos-testes.apps.googleusercontent.com";
    private static final String DOMINIO = "fmpsc.edu.br";
    private static final String DOMINIOS = "fmpsc.edu.br, Aluno.FMPSC.edu.br";

    private static Jwt token(UnaryOperator<Jwt.Builder> ajuste) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .audience(List.of(CLIENT_ID))
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", "Iago.Barbosa@fmpsc.edu.br")
                .claim("email_verified", true)
                .claim("hd", DOMINIO)
                .claim("name", "Iago Barbosa");
        return ajuste.apply(builder).build();
    }

    private static GoogleTokenVerifier verificadorQueDevolve(Jwt jwt) {
        return new GoogleTokenVerifier(DOMINIOS, credential -> jwt);
    }

    private static void assertRecusado(Runnable acao, HttpStatus status) {
        assertThatThrownBy(acao::run)
                .isInstanceOf(AcessoRecusadoException.class)
                .extracting("status").isEqualTo(status);
    }

    @Test
    void contaInstitucionalVerificadaEAceitaComEmailNormalizado() {
        IdentidadeGoogle identidade = verificadorQueDevolve(token(b -> b)).verificar("x");

        assertThat(identidade.email()).isEqualTo("iago.barbosa@fmpsc.edu.br");
        assertThat(identidade.nome()).isEqualTo("Iago Barbosa");
    }

    // Alunos com e-mail @aluno.fmpsc.edu.br: o hd pode ser o dominio principal
    // (dominio secundario na mesma organizacao) ou o proprio subdominio
    @Test
    void contaDeAlunoEAceitaComQualquerHdDaLista() {
        Jwt hdPrincipal = token(b -> b.claim("email", "maria@aluno.fmpsc.edu.br"));
        Jwt hdProprio = token(b -> b.claim("email", "maria@aluno.fmpsc.edu.br").claim("hd", "aluno.fmpsc.edu.br"));

        assertThat(verificadorQueDevolve(hdPrincipal).verificar("x").email()).isEqualTo("maria@aluno.fmpsc.edu.br");
        assertThat(verificadorQueDevolve(hdProprio).verificar("x").email()).isEqualTo("maria@aluno.fmpsc.edu.br");
    }

    @Test
    void contaDeOutraOrganizacaoComEmailDeAlunoERecusada() {
        Jwt outraOrganizacao = token(b -> b.claim("email", "maria@aluno.fmpsc.edu.br").claim("hd", "outra.edu.br"));

        assertRecusado(() -> verificadorQueDevolve(outraOrganizacao).verificar("x"), HttpStatus.FORBIDDEN);
    }

    @Test
    void mensagemDeRecusaListaOsDominiosAceitos() {
        Jwt gmail = token(b -> b.claim("email", "iago@gmail.com").claims(c -> c.remove("hd")));

        assertThatThrownBy(() -> verificadorQueDevolve(gmail).verificar("x"))
                .hasMessage("Use sua conta institucional (@fmpsc.edu.br ou @aluno.fmpsc.edu.br) para entrar.");
    }

    @Test
    void contaSemNomeUsaOInicioDoEmail() {
        IdentidadeGoogle identidade = verificadorQueDevolve(token(b -> b.claims(c -> c.remove("name"))))
                .verificar("x");

        assertThat(identidade.nome()).isEqualTo("iago.barbosa");
    }

    // Conta pessoal (@gmail.com) nao tem o claim "hd"
    @Test
    void contaGooglePessoalERecusada() {
        Jwt gmail = token(b -> b.claim("email", "iago@gmail.com").claims(c -> c.remove("hd")));

        assertRecusado(() -> verificadorQueDevolve(gmail).verificar("x"), HttpStatus.FORBIDDEN);
    }

    @Test
    void contaDeOutraInstituicaoERecusada() {
        Jwt outra = token(b -> b.claim("email", "aluno@outra.edu.br").claim("hd", "outra.edu.br"));

        assertRecusado(() -> verificadorQueDevolve(outra).verificar("x"), HttpStatus.FORBIDDEN);
    }

    @Test
    void hdDaInstituicaoComEmailDeOutroDominioERecusado() {
        Jwt misturado = token(b -> b.claim("email", "aluno@gmail.com"));

        assertRecusado(() -> verificadorQueDevolve(misturado).verificar("x"), HttpStatus.FORBIDDEN);
    }

    @Test
    void emailNaoVerificadoERecusado() {
        Jwt naoVerificado = token(b -> b.claim("email_verified", false));

        assertRecusado(() -> verificadorQueDevolve(naoVerificado).verificar("x"), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenInvalidoERecusado() {
        GoogleTokenVerifier verificador = new GoogleTokenVerifier(DOMINIOS, credential -> {
            throw new BadJwtException("assinatura invalida");
        });

        assertRecusado(() -> verificador.verificar("x"), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void semClientIdConfiguradoOLoginComGoogleFicaIndisponivel() {
        GoogleTokenVerifier verificador = new GoogleTokenVerifier("", DOMINIOS);

        assertRecusado(() -> verificador.verificar("x"), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void validadorAceitaTokenEmitidoPeloGoogleParaEsteSistema() {
        assertThat(GoogleTokenVerifier.validador(CLIENT_ID).validate(token(b -> b)).hasErrors()).isFalse();
        assertThat(GoogleTokenVerifier.validador(CLIENT_ID)
                .validate(token(b -> b.issuer("accounts.google.com"))).hasErrors()).isFalse();
    }

    // Token valido do Google, mas gerado para outro site
    @Test
    void validadorRecusaTokenDeOutroClientId() {
        Jwt outroSite = token(b -> b.audience(List.of("outro-site.apps.googleusercontent.com")));

        assertThat(GoogleTokenVerifier.validador(CLIENT_ID).validate(outroSite).hasErrors()).isTrue();
    }

    @Test
    void validadorRecusaOutroEmissor() {
        Jwt outroEmissor = token(b -> b.issuer("https://login.microsoftonline.com"));

        assertThat(GoogleTokenVerifier.validador(CLIENT_ID).validate(outroEmissor).hasErrors()).isTrue();
    }

    @Test
    void validadorRecusaTokenExpirado() {
        Jwt expirado = token(b -> b
                .issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600)));

        assertThat(GoogleTokenVerifier.validador(CLIENT_ID).validate(expirado).hasErrors()).isTrue();
    }
}
