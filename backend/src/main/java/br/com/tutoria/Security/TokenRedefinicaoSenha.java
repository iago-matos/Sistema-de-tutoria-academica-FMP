package br.com.tutoria.Security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.function.LongFunction;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.tutoria.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// Token do link "redefinir senha" (RF02). Nao fica gravado no banco:
// - Vale por 30 minutos.
// - E assinado com uma chave DERIVADA do JWT_SECRET, diferente da chave do
//   login. Assim ele nao serve como token de acesso, e o token de acesso nao
//   serve para trocar a senha.
// - Carrega uma "impressao" da senha atual (hash do hash BCrypt). Quando a
//   senha muda, a impressao deixa de bater e o link perde a validade: cada
//   link funciona uma vez so, e links antigos morrem quando a senha e trocada.
@Component
public class TokenRedefinicaoSenha {

    public static final Duration VALIDADE = Duration.ofMinutes(30);
    private static final String FINALIDADE = "redefinir-senha";

    private final SecretKey chave;

    public TokenRedefinicaoSenha(@Value("${jwt.secret}") String segredo) {
        this.chave = Keys.hmacShaKeyFor(sha256(segredo + "|" + FINALIDADE));
    }

    public String gerar(Usuario usuario, Instant agora) {
        return Jwts.builder()
                .subject(String.valueOf(usuario.getId()))
                .claim("fin", FINALIDADE)
                .claim("imp", impressao(usuario.getSenha()))
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(VALIDADE)))
                .signWith(chave)
                .compact();
    }

    // Id do usuario dono do token. Lanca IllegalArgumentException se o token for
    // invalido, adulterado, vencido ou se a senha ja tiver sido trocada.
    public Long validar(String token, LongFunction<Usuario> buscarUsuario) {
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw linkInvalido();
        }
        if (!FINALIDADE.equals(claims.get("fin", String.class))) {
            throw linkInvalido();
        }

        long usuarioId;
        try {
            usuarioId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw linkInvalido();
        }

        Usuario usuario = buscarUsuario.apply(usuarioId);
        String esperada = usuario == null ? null : impressao(usuario.getSenha());
        String recebida = claims.get("imp", String.class);
        if (esperada == null || recebida == null
                || !MessageDigest.isEqual(esperada.getBytes(StandardCharsets.UTF_8),
                        recebida.getBytes(StandardCharsets.UTF_8))) {
            throw linkInvalido();
        }
        return usuarioId;
    }

    private static IllegalArgumentException linkInvalido() {
        return new IllegalArgumentException(
                "Este link de redefinição é inválido, já foi usado ou expirou. Peça um novo link.");
    }

    private static String impressao(String hashDaSenha) {
        byte[] digest = sha256(hashDaSenha == null ? "" : hashDaSenha);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, 16));
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
