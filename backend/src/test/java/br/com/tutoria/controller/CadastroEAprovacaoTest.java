package br.com.tutoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.ProfileEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.support.IntegracaoTestBase;

class CadastroEAprovacaoTest extends IntegracaoTestBase {

    private Map<String, Object> cadastroEstudante(String email) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", "Ana Souza");
        corpo.put("email", email);
        corpo.put("senha", SENHA_PADRAO);
        corpo.put("tipo", "ESTUDANTE");
        return corpo;
    }

    private void cadastrar(Map<String, Object> corpo) throws Exception {
        mockMvc.perform(post("/usuario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corpo)))
                .andExpect(status().isCreated());
    }

    @Test
    void cadastroDeEstudanteCriaContaPendenteComPerfil() throws Exception {
        mockMvc.perform(post("/usuario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(cadastroEstudante("Ana@Teste.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.tipo").value("ESTUDANTE"))
                .andExpect(jsonPath("$.email").value("ana@teste.com"))
                .andExpect(jsonPath("$.senha").doesNotExist());

        Usuario usuario = usuarioRepository.findByEmail("ana@teste.com").orElseThrow();
        assertThat(estudanteRepository.findByUsuario_Id(usuario.getId())).isPresent();
    }

    @Test
    void cadastroDeTutorCriaPerfilDeTutor() throws Exception {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", "Bruno Lima");
        corpo.put("email", "bruno@teste.com");
        corpo.put("senha", SENHA_PADRAO);
        corpo.put("tipo", "TUTOR");
        corpo.put("disciplinaId", disciplinaService.buscarPorNome("Banco de Dados").orElseThrow().getId());

        cadastrar(corpo);

        Usuario usuario = usuarioRepository.findByEmail("bruno@teste.com").orElseThrow();
        Long tutorId = tutorRepository.findByUsuario_Id(usuario.getId()).orElseThrow().getId();
        assertThat(disciplinaService.ativasDoTutor(tutorId))
                .extracting("nome").containsExactly("Banco de Dados");
    }

    // Antes desta correcao, enviar "profiles": ["ADMIN"] no cadastro criava um administrador
    @Test
    void cadastroIgnoraPerfisEStatusEnviadosPeloCliente() throws Exception {
        Map<String, Object> corpo = cadastroEstudante("invasor@teste.com");
        corpo.put("profiles", new String[] {"ADMIN"});
        corpo.put("status", "ATIVO");

        cadastrar(corpo);

        Usuario usuario = usuarioRepository.findByEmail("invasor@teste.com").orElseThrow();
        assertThat(usuario.getProfiles()).containsExactly(ProfileEnum.USER);
        assertThat(usuario.getStatus()).isEqualTo(UsuarioStatusEnum.PENDENTE);
    }

    @Test
    void cadastroComoAdminERecusado() throws Exception {
        Map<String, Object> corpo = cadastroEstudante("quer-ser-admin@teste.com");
        corpo.put("tipo", "ADMIN");

        mockMvc.perform(post("/usuario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corpo)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastroDeTutorSemDisciplinaERecusado() throws Exception {
        Map<String, Object> corpo = cadastroEstudante("tutor-incompleto@teste.com");
        corpo.put("tipo", "TUTOR");

        mockMvc.perform(post("/usuario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("disciplina")));

        assertThat(usuarioRepository.findByEmail("tutor-incompleto@teste.com")).isEmpty();
    }

    @Test
    void cadastroComSenhaCurtaERecusado() throws Exception {
        Map<String, Object> corpo = cadastroEstudante("senha-curta@teste.com");
        corpo.put("senha", "1234");

        mockMvc.perform(post("/usuario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corpo)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("senha"));
    }

    @Test
    void cadastroComEmailJaUsadoERecusadoMesmoComMaiusculas() throws Exception {
        cadastrar(cadastroEstudante("repetido@teste.com"));

        mockMvc.perform(post("/usuario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(cadastroEstudante("REPETIDO@teste.com"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginDeContaPendenteRetorna403ComMotivo() throws Exception {
        cadastrar(cadastroEstudante("pendente@teste.com"));

        login("pendente@teste.com", SENHA_PADRAO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("aguardando")))
                .andExpect(header().doesNotExist("Authorization"));
    }

    // Quem nao sabe a senha nao pode descobrir que o cadastro existe e esta pendente
    @Test
    void loginDeContaPendenteComSenhaErradaNaoRevelaOStatus() throws Exception {
        cadastrar(cadastroEstudante("pendente2@teste.com"));

        login("pendente2@teste.com", "senha-errada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais invalidas"));
    }

    @Test
    void adminAprovaCadastroEUsuarioPassaALogar() throws Exception {
        cadastrar(cadastroEstudante("aprovado@teste.com"));
        Long id = usuarioRepository.findByEmail("aprovado@teste.com").orElseThrow().getId();

        mockMvc.perform(get("/usuario/pendentes").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'aprovado@teste.com')].tipo").value("ESTUDANTE"));

        mockMvc.perform(put("/usuario/" + id + "/aprovar").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVO"));

        login("aprovado@teste.com", SENHA_PADRAO)
                .andExpect(status().isOk())
                .andExpect(header().string("Authorization", startsWith("Bearer ")));
    }

    @Test
    void adminRecusaCadastroELoginInformaARecusa() throws Exception {
        cadastrar(cadastroEstudante("recusado@teste.com"));
        Long id = usuarioRepository.findByEmail("recusado@teste.com").orElseThrow().getId();

        mockMvc.perform(put("/usuario/" + id + "/recusar").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADO"));

        login("recusado@teste.com", SENHA_PADRAO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("recusado")));
    }

    @Test
    void usuarioComumNaoPodeAprovarCadastros() throws Exception {
        Usuario comum = criarEstudante("comum@teste.com").getUsuario();
        Usuario pendente = criarUsuario("outro-pendente@teste.com", UsuarioStatusEnum.PENDENTE);

        mockMvc.perform(put("/usuario/" + pendente.getId() + "/aprovar")
                .header("Authorization", tokenDe(comum)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/usuario/pendentes").header("Authorization", tokenDe(comum)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenDeContaQueDeixouDeEstarAtivaNaoEAceito() throws Exception {
        Usuario usuario = criarUsuario("desativado@teste.com", UsuarioStatusEnum.ATIVO);
        String token = tokenDe(usuario);

        usuario.setStatus(UsuarioStatusEnum.RECUSADO);
        usuarioRepository.save(usuario);

        mockMvc.perform(get("/usuario/me").header("Authorization", token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void administradorInicialECriadoAtivo() throws Exception {
        Usuario admin = usuarioRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

        assertThat(admin.getProfiles()).contains(ProfileEnum.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UsuarioStatusEnum.ATIVO);

        login(ADMIN_EMAIL, ADMIN_SENHA).andExpect(status().isOk());
    }
}
