package br.com.tutoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import br.com.tutoria.Security.GoogleTokenVerifier;
import br.com.tutoria.Security.GoogleTokenVerifier.IdentidadeGoogle;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.ProfileEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.service.exceptions.AcessoRecusadoException;
import br.com.tutoria.support.IntegracaoTestBase;

// Fluxo do login com Google. A conferencia do token em si (assinatura, dominio)
// esta no GoogleTokenVerifierTest; aqui o verificador devolve a identidade pronta.
// Com o cadastro com senha desligado, como em producao.
@TestPropertySource(properties = "app.cadastro-com-senha=false")
class LoginGoogleTest extends IntegracaoTestBase {

    private static final String EMAIL = "aluno.novo@fmpsc.edu.br";

    @MockitoBean
    private GoogleTokenVerifier googleTokenVerifier;

    @BeforeEach
    void googleConfirmaAConta() {
        when(googleTokenVerifier.verificar(anyString()))
                .thenReturn(new IdentidadeGoogle(EMAIL, "Aluno Novo"));
    }

    private ResultActions entrarComGoogle(Map<String, Object> extras) throws Exception {
        Map<String, Object> corpo = new HashMap<>(extras);
        corpo.put("credential", "id-token-do-google");
        return mockMvc.perform(post("/login/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corpo)));
    }

    @Test
    void configuracaoDoLoginEPublica() throws Exception {
        mockMvc.perform(get("/login/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleClientId").value("client-id-dos-testes.apps.googleusercontent.com"))
                .andExpect(jsonPath("$.dominiosInstitucionais[0]").value("fmpsc.edu.br"))
                .andExpect(jsonPath("$.dominiosInstitucionais[1]").value("aluno.fmpsc.edu.br"))
                .andExpect(jsonPath("$.cadastroComSenha").value(false));
    }

    @Test
    void contaExistenteAtivaEntraERecebeToken() throws Exception {
        criarEstudante(EMAIL);

        entrarComGoogle(Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("AUTENTICADO"))
                .andExpect(header().string("Authorization", startsWith("Bearer ")));
    }

    @Test
    void tokenDoLoginComGoogleDaAcessoAsRotasProtegidas() throws Exception {
        criarEstudante(EMAIL);

        String token = entrarComGoogle(Map.of()).andReturn().getResponse().getHeader("Authorization");

        mockMvc.perform(get("/usuario/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.tipo").value("ESTUDANTE"));
    }

    @Test
    void primeiroAcessoSemPerfilPedeOCadastroENaoCriaConta() throws Exception {
        entrarComGoogle(Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CADASTRO_NECESSARIO"))
                .andExpect(jsonPath("$.nome").value("Aluno Novo"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(header().doesNotExist("Authorization"));

        assertThat(usuarioRepository.findByEmail(EMAIL)).isEmpty();
    }

    // O estudante so escolhe o tipo: os dias vem da agenda do tutor
    @Test
    void primeiroAcessoDeEstudanteSoPrecisaDoTipoECriaContaAtiva() throws Exception {
        entrarComGoogle(Map.of("tipo", "ESTUDANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("AUTENTICADO"))
                .andExpect(header().string("Authorization", startsWith("Bearer ")));

        Usuario usuario = usuarioRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(usuario.getStatus()).isEqualTo(UsuarioStatusEnum.ATIVO);
        assertThat(usuario.getNome()).isEqualTo("Aluno Novo");
        assertThat(usuario.getProfiles()).containsExactly(ProfileEnum.USER);
        assertThat(estudanteRepository.findByUsuario_Id(usuario.getId())).isPresent();
    }

    // Clientes antigos que ainda enviam o dia da semana nao recebem erro
    @Test
    void primeiroAcessoIgnoraDiaDaSemanaEnviadoPorClienteAntigo() throws Exception {
        entrarComGoogle(Map.of("tipo", "ESTUDANTE", "diaDaSemana", "Quarta-feira"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("AUTENTICADO"));
    }

    @Test
    void primeiroAcessoDeTutorFicaPendenteAteAAprovacao() throws Exception {
        Long bancoDeDados = disciplinaService.buscarPorNome("Banco de Dados").orElseThrow().getId();
        entrarComGoogle(Map.of("tipo", "TUTOR", "disciplinaId", bancoDeDados))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.situacao").value("AGUARDANDO_APROVACAO"))
                .andExpect(header().doesNotExist("Authorization"));

        Usuario usuario = usuarioRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(usuario.getStatus()).isEqualTo(UsuarioStatusEnum.PENDENTE);
        Long tutorId = tutorRepository.findByUsuario_Id(usuario.getId()).orElseThrow().getId();
        assertThat(disciplinaService.ativasDoTutor(tutorId)).extracting("nome").containsExactly("Banco de Dados");

        // Tentar de novo antes da aprovacao informa o motivo
        entrarComGoogle(Map.of())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("aguardando")));
    }

    @Test
    void primeiroAcessoNaoPodeCriarAdministrador() throws Exception {
        entrarComGoogle(Map.of("tipo", "ADMIN"))
                .andExpect(status().isBadRequest());

        assertThat(usuarioRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    void primeiroAcessoDeTutorSemDisciplinaERecusado() throws Exception {
        entrarComGoogle(Map.of("tipo", "TUTOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("disciplina")));

        assertThat(usuarioRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    void contaRecusadaRecebe403ComMotivo() throws Exception {
        criarUsuario(EMAIL, UsuarioStatusEnum.RECUSADO);

        entrarComGoogle(Map.of())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("recusado")));
    }

    @Test
    void contaRecusadaPeloVerificadorNaoEntra() throws Exception {
        when(googleTokenVerifier.verificar(anyString())).thenThrow(new AcessoRecusadoException(
                HttpStatus.FORBIDDEN, "Use sua conta institucional @fmpsc.edu.br para entrar."));

        entrarComGoogle(Map.of("tipo", "ESTUDANTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("@fmpsc.edu.br")));

        assertThat(usuarioRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    void semTokenDoGoogleERecusado() throws Exception {
        mockMvc.perform(post("/login/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cadastroComSenhaDesligadoERecusado() throws Exception {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", "Ana Souza");
        corpo.put("email", "ana@fmpsc.edu.br");
        corpo.put("senha", SENHA_PADRAO);
        corpo.put("tipo", "ESTUDANTE");

        mockMvc.perform(post("/usuario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corpo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Google")));

        assertThat(usuarioRepository.findByEmail("ana@fmpsc.edu.br")).isEmpty();
    }

    // Contas antigas (admin, contas de teste) continuam entrando com senha
    @Test
    void loginComSenhaContinuaFuncionando() throws Exception {
        login(ADMIN_EMAIL, ADMIN_SENHA)
                .andExpect(status().isOk())
                .andExpect(header().string("Authorization", startsWith("Bearer ")));
    }
}
