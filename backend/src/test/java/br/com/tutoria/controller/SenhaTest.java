package br.com.tutoria.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.support.IntegracaoTestBase;

class SenhaTest extends IntegracaoTestBase {

    private static final String NOVA_SENHA = "NovaSenha2026";

    private ResultActions alterarSenha(
            Usuario usuario, String senhaAtual, String novaSenha) throws Exception {
        return mockMvc.perform(put("/usuario/me/senha")
                .header("Authorization", tokenDe(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("senhaAtual", senhaAtual, "novaSenha", novaSenha))));
    }

    @Test
    void usuarioAlteraAPropriaSenhaInformandoAAtual() throws Exception {
        Usuario usuario = criarUsuario("aluno@teste.com", UsuarioStatusEnum.ATIVO);

        alterarSenha(usuario, SENHA_PADRAO, NOVA_SENHA).andExpect(status().isNoContent());

        login("aluno@teste.com", NOVA_SENHA).andExpect(status().isOk());
        login("aluno@teste.com", SENHA_PADRAO).andExpect(status().isUnauthorized());
    }

    @Test
    void alteracaoComSenhaAtualErradaERecusada() throws Exception {
        Usuario usuario = criarUsuario("aluno@teste.com", UsuarioStatusEnum.ATIVO);

        alterarSenha(usuario, "senha-errada", NOVA_SENHA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Senha atual incorreta"));

        login("aluno@teste.com", SENHA_PADRAO).andExpect(status().isOk());
    }

    @Test
    void novaSenhaCurtaERecusada() throws Exception {
        Usuario usuario = criarUsuario("aluno@teste.com", UsuarioStatusEnum.ATIVO);

        alterarSenha(usuario, SENHA_PADRAO, "1234")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("novaSenha"));
    }

    @Test
    void novaSenhaIgualAAtualERecusada() throws Exception {
        Usuario usuario = criarUsuario("aluno@teste.com", UsuarioStatusEnum.ATIVO);

        alterarSenha(usuario, SENHA_PADRAO, SENHA_PADRAO).andExpect(status().isBadRequest());
    }

    // Antes, a tela de usuarios do admin enviava senha "placeholder" ao editar
    // o nome, e a senha da pessoa era trocada sem ela saber
    @Test
    void editarDadosDoUsuarioNaoAlteraASenha() throws Exception {
        Usuario usuario = criarUsuario("aluno@teste.com", UsuarioStatusEnum.ATIVO);

        mockMvc.perform(put("/usuario/" + usuario.getId())
                .header("Authorization", tokenAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nome", "Nome Corrigido", "senha", "placeholder"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome Corrigido"));

        login("aluno@teste.com", SENHA_PADRAO).andExpect(status().isOk());
        login("aluno@teste.com", "placeholder").andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioEditaOsPropriosDadosMasNaoOsDeOutro() throws Exception {
        Usuario ana = criarUsuario("ana@teste.com", UsuarioStatusEnum.ATIVO);
        Usuario bia = criarUsuario("bia@teste.com", UsuarioStatusEnum.ATIVO);

        mockMvc.perform(put("/usuario/" + ana.getId())
                .header("Authorization", tokenDe(ana))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nome", "Ana Souza"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/usuario/" + bia.getId())
                .header("Authorization", tokenDe(ana))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nome", "Invadido"))))
                .andExpect(status().isForbidden());
    }
}
