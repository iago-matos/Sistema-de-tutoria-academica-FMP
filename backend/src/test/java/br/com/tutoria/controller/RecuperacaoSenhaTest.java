package br.com.tutoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.support.IntegracaoTestBase;

// RF02 - recuperacao de senha pelo link de redefinicao
class RecuperacaoSenhaTest extends IntegracaoTestBase {

    private static final String NOVA_SENHA = "NovaSenha2026";

    private String gerarToken(Usuario usuario) throws Exception {
        String corpo = mockMvc.perform(post("/usuario/" + usuario.getId() + "/redefinicao-senha")
                .header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiraEm").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("token").asText();
    }

    private ResultActions redefinir(String token, String novaSenha) throws Exception {
        return mockMvc.perform(post("/login/redefinir-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", token, "novaSenha", novaSenha))));
    }

    @Test
    void linkDaCoordenacaoPermiteCriarNovaSenhaUmaVezSo() throws Exception {
        Usuario usuario = criarUsuario("prof@teste.com", UsuarioStatusEnum.ATIVO);
        String token = gerarToken(usuario);

        redefinir(token, NOVA_SENHA).andExpect(status().isNoContent());

        login("prof@teste.com", NOVA_SENHA).andExpect(status().isOk());
        login("prof@teste.com", SENHA_PADRAO).andExpect(status().isUnauthorized());

        // O mesmo link nao funciona de novo
        redefinir(token, "OutraSenha2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("inválido")));
    }

    @Test
    void trocarASenhaInvalidaLinksAntigos() throws Exception {
        Usuario usuario = criarUsuario("prof@teste.com", UsuarioStatusEnum.ATIVO);
        String antigo = gerarToken(usuario);
        String novo = gerarToken(usuario);

        redefinir(novo, NOVA_SENHA).andExpect(status().isNoContent());
        redefinir(antigo, "OutraSenha2026").andExpect(status().isBadRequest());
    }

    @Test
    void linkAdulteradoOuTokenDeLoginNaoServem() throws Exception {
        Usuario usuario = criarUsuario("prof@teste.com", UsuarioStatusEnum.ATIVO);
        String token = gerarToken(usuario);

        redefinir(token + "x", NOVA_SENHA).andExpect(status().isBadRequest());
        // O token de acesso (login) e assinado com outra chave
        redefinir(tokenDe(usuario).substring("Bearer ".length()), NOVA_SENHA).andExpect(status().isBadRequest());
        // E o link de redefinicao nao serve como token de acesso
        mockMvc.perform(get("/usuario/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        login("prof@teste.com", SENHA_PADRAO).andExpect(status().isOk());
    }

    @Test
    void novaSenhaCurtaERecusada() throws Exception {
        Usuario usuario = criarUsuario("prof@teste.com", UsuarioStatusEnum.ATIVO);

        redefinir(gerarToken(usuario), "1234")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("novaSenha"));
    }

    @Test
    void contaQueDeixouDeEstarAtivaNaoRedefine() throws Exception {
        Usuario usuario = criarUsuario("prof@teste.com", UsuarioStatusEnum.ATIVO);
        String token = gerarToken(usuario);

        usuario.setStatus(UsuarioStatusEnum.INATIVO);
        usuarioRepository.save(usuario);

        redefinir(token, NOVA_SENHA).andExpect(status().isBadRequest());
    }

    @Test
    void soACoordenacaoGeraOLink() throws Exception {
        Usuario usuario = criarUsuario("prof@teste.com", UsuarioStatusEnum.ATIVO);
        Usuario outro = criarUsuario("outro@teste.com", UsuarioStatusEnum.ATIVO);

        mockMvc.perform(post("/usuario/" + usuario.getId() + "/redefinicao-senha")
                .header("Authorization", tokenDe(outro)))
                .andExpect(status().isForbidden());

        Usuario pendente = criarUsuario("pendente@teste.com", UsuarioStatusEnum.PENDENTE);
        mockMvc.perform(post("/usuario/" + pendente.getId() + "/redefinicao-senha")
                .header("Authorization", tokenAdmin()))
                .andExpect(status().isBadRequest());
    }

    // Sem servidor de e-mail (como nos testes): a tela de login nao oferece o envio,
    // e o pedido responde igual para e-mail cadastrado ou nao
    @Test
    void esqueciSenhaNaoRevelaSeOEmailExiste() throws Exception {
        criarUsuario("prof@teste.com", UsuarioStatusEnum.ATIVO);

        mockMvc.perform(get("/login/config"))
                .andExpect(jsonPath("$.recuperacaoSenhaPorEmail").value(false));

        String existente = mockMvc.perform(post("/login/esqueci-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "prof@teste.com"))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String inexistente = mockMvc.perform(post("/login/esqueci-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "ninguem@teste.com"))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(inexistente).isEqualTo(existente);
    }
}
