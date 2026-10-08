package br.com.tutoria.Security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.tutoria.model.enums.ProfileEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;

class JWTUtilTest {

    private JWTUtil criar(String segredo) {
        JWTUtil jwtUtil = new JWTUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", segredo);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 60_000L);
        return jwtUtil;
    }

    @Test
    void recusaSegredoAusente() {
        assertThatThrownBy(() -> criar("").validarSegredo())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void recusaSegredoComMenosDe32Caracteres() {
        assertThatThrownBy(() -> criar("curto-demais").validarSegredo())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void geraEValidaTokenComSegredoValido() {
        JWTUtil jwtUtil = criar("um-segredo-valido-com-mais-de-32-caracteres");
        jwtUtil.validarSegredo();

        UserSpringSecurity usuario = new UserSpringSecurity(
                1L, "aluno@teste.com", "hash", Set.of(ProfileEnum.USER), UsuarioStatusEnum.ATIVO);

        String token = jwtUtil.generateToken(usuario);

        assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo("aluno@teste.com");
        assertThat(jwtUtil.validateToken(token, usuario)).isTrue();
    }
}
