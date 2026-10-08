package br.com.tutoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Avaliacao;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.ProfileEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.repository.AvaliacaoRepository;
import br.com.tutoria.support.IntegracaoTestBase;

class PermissoesTest extends IntegracaoTestBase {

    @Autowired private AgendamentoRepository agendamentoRepository;
    @Autowired private AvaliacaoRepository avaliacaoRepository;

    private Agendamento criarSessao(Tutor tutor, String status, Estudante estudante) {
        Agendamento agendamento = new Agendamento();
        agendamento.setTutor(tutor);
        agendamento.setEstudante(estudante);
        agendamento.setStatus(status);
        agendamento.setData(LocalDate.now().plusDays(3));
        agendamento.setHora(LocalTime.of(14, 0));
        return agendamentoRepository.save(agendamento);
    }

    private Avaliacao criarAvaliacao(Tutor tutor, Estudante estudante, int nota) {
        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setTutor(tutor);
        avaliacao.setEstudante(estudante);
        avaliacao.setNota(nota);
        avaliacao.setDataAvaliacao(LocalDate.now());
        avaliacao.setHoraAvaliacao(LocalTime.of(10, 0));
        return avaliacaoRepository.save(avaliacao);
    }

    // ── Bloqueante 1: ninguem vira administrador por conta propria ───────────

    @Test
    void estudanteNaoPodeCadastrarCoordenador() throws Exception {
        Usuario aluno = criarEstudante("aluno@teste.com").getUsuario();

        mockMvc.perform(post("/coordenador")
                .header("Authorization", tokenDe(aluno))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("usuario", Map.of("id", aluno.getId())))))
                .andExpect(status().isForbidden());

        assertThat(usuarioRepository.findById(aluno.getId()).orElseThrow().getProfiles())
                .doesNotContain(ProfileEnum.ADMIN);
    }

    @Test
    void cadastrarCoordenadorNaoConcedePerfilAdmin() throws Exception {
        Usuario professor = criarUsuario("coordenacao@teste.com", UsuarioStatusEnum.ATIVO);

        mockMvc.perform(post("/coordenador")
                .header("Authorization", tokenAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("usuario", Map.of("id", professor.getId())))))
                .andExpect(status().isCreated());

        assertThat(usuarioRepository.findById(professor.getId()).orElseThrow().getProfiles())
                .doesNotContain(ProfileEnum.ADMIN);

        mockMvc.perform(get("/usuario/me").header("Authorization", tokenDe(professor)))
                .andExpect(jsonPath("$.tipo").value("ESTUDANTE"));
    }

    // ── Bloqueante 2: ninguem mexe nos dados dos outros ──────────────────────

    @Test
    void estudanteNaoPodeExcluirNemEditarAgendamentosEAvaliacoes() throws Exception {
        Estudante aluno = criarEstudante("aluno@teste.com");
        Tutor tutor = criarTutor("tutor@teste.com");
        Agendamento sessao = criarSessao(tutor, "PENDENTE", aluno);
        Avaliacao avaliacao = criarAvaliacao(tutor, aluno, 5);
        String token = tokenDe(aluno.getUsuario());

        mockMvc.perform(delete("/agendamento/" + sessao.getId()).header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/avaliacao/" + avaliacao.getId()).header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/avaliacao/" + avaliacao.getId())
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nota", 1))))
                .andExpect(status().isForbidden());

        assertThat(agendamentoRepository.findById(sessao.getId())).isPresent();
        assertThat(avaliacaoRepository.findById(avaliacao.getId()).orElseThrow().getNota()).isEqualTo(5);
    }

    @Test
    void estudanteNaoPodeAlterarStatusDeAgendamento() throws Exception {
        Estudante aluno = criarEstudante("aluno@teste.com");
        Agendamento sessao = criarSessao(criarTutor("tutor@teste.com"), "PENDENTE", aluno);

        mockMvc.perform(put("/agendamento/" + sessao.getId())
                .header("Authorization", tokenDe(aluno.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "CONFIRMADO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void tutorSoAlteraAsPropriasSessoes() throws Exception {
        Tutor dono = criarTutor("dono@teste.com");
        Tutor outro = criarTutor("outro@teste.com");
        Agendamento sessao = criarSessao(dono, "PENDENTE", criarEstudante("aluno@teste.com"));

        mockMvc.perform(put("/agendamento/" + sessao.getId())
                .header("Authorization", tokenDe(outro.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "CANCELADO"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/agendamento/" + sessao.getId())
                .header("Authorization", tokenDe(dono.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "CONFIRMADO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADO"));
    }

    @Test
    void estudanteNaoVeAgendamentosNemDadosDeOutroEstudante() throws Exception {
        Estudante ana = criarEstudante("ana@teste.com");
        Estudante bia = criarEstudante("bia@teste.com");
        Agendamento sessaoDaBia = criarSessao(criarTutor("tutor@teste.com"), "CONFIRMADO", bia);
        String tokenAna = tokenDe(ana.getUsuario());

        mockMvc.perform(get("/estudante/" + bia.getId() + "/agendamentos").header("Authorization", tokenAna))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/agendamento/estudante/" + bia.getId()).header("Authorization", tokenAna))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/agendamento/" + sessaoDaBia.getId()).header("Authorization", tokenAna))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/estudante/" + bia.getId()).header("Authorization", tokenAna))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/estudante/" + ana.getId() + "/agendamentos").header("Authorization", tokenAna))
                .andExpect(status().isOk());
    }

    @Test
    void listasCompletasSaoExclusivasDoAdmin() throws Exception {
        String tokenAluno = tokenDe(criarEstudante("aluno@teste.com").getUsuario());

        mockMvc.perform(get("/estudante").header("Authorization", tokenAluno))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/avaliacao").header("Authorization", tokenAluno))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/agendamento").header("Authorization", tokenAluno))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/coordenador").header("Authorization", tokenAluno))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/estudante").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk());
    }

    // ── Identidade sempre pelo token, nunca pelo corpo da requisicao ─────────

    @Test
    void estudanteSolicitaSessaoSempreEmNomeProprio() throws Exception {
        Estudante ana = criarEstudante("ana@teste.com");
        Estudante bia = criarEstudante("bia@teste.com");
        Agendamento sessao = criarSessao(criarTutor("tutor@teste.com"), "DISPONIVEL", null);

        // Ana tenta solicitar a sessao em nome da Bia
        mockMvc.perform(put("/agendamento/" + sessao.getId() + "/solicitar")
                .header("Authorization", tokenDe(ana.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("estudanteId", bia.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estudante.id").value(ana.getId()))
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    void tutorCriaSessaoSempreNaPropriaAgenda() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Tutor outro = criarTutor("outro@teste.com");

        mockMvc.perform(post("/agendamento")
                .header("Authorization", tokenDe(tutor.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "data", LocalDate.now().plusDays(1).toString(),
                        "hora", "09:00",
                        "tutor", Map.of("id", outro.getId())))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tutor.id").value(tutor.getId()))
                .andExpect(jsonPath("$.status").value("DISPONIVEL"));
    }

    @Test
    void estudanteNaoPodeCriarSessao() throws Exception {
        mockMvc.perform(post("/agendamento")
                .header("Authorization", tokenDe(criarEstudante("aluno@teste.com").getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("data", LocalDate.now().plusDays(1).toString(), "hora", "09:00"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void avaliacaoERegistradaEmNomeDoEstudanteLogado() throws Exception {
        Estudante ana = criarEstudante("ana@teste.com");
        Estudante bia = criarEstudante("bia@teste.com");
        Tutor tutor = criarTutor("tutor@teste.com");

        mockMvc.perform(post("/avaliacao")
                .header("Authorization", tokenDe(ana.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "nota", 4,
                        "dataAvaliacao", LocalDate.now().toString(),
                        "horaAvaliacao", "10:00",
                        "tutor", Map.of("id", tutor.getId()),
                        "estudante", Map.of("id", bia.getId())))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estudante.id").value(ana.getId()));
    }

    @Test
    void tutorNaoPodeRegistrarAvaliacao() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");

        mockMvc.perform(post("/avaliacao")
                .header("Authorization", tokenDe(tutor.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "nota", 5,
                        "dataAvaliacao", LocalDate.now().toString(),
                        "horaAvaliacao", "10:00",
                        "tutor", Map.of("id", tutor.getId())))))
                .andExpect(status().isForbidden());
    }

    // ── Privacidade e dados exibidos ─────────────────────────────────────────

    @Test
    void listaDeTutoresMostraMediaRealSemEmailESemPendentes() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante aluno = criarEstudante("aluno@teste.com");
        criarAvaliacao(tutor, aluno, 4);
        criarAvaliacao(tutor, aluno, 5);

        Usuario pendente = criarUsuario("tutor-pendente@teste.com", UsuarioStatusEnum.PENDENTE);
        Tutor tutorPendente = new Tutor();
        tutorPendente.setUsuario(pendente);
        tutorPendente.setDisciplina("Redes");
        tutorRepository.save(tutorPendente);

        mockMvc.perform(get("/tutor").header("Authorization", tokenDe(aluno.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + tutor.getId() + ")].mediaAvaliacoes").value(4.5))
                .andExpect(jsonPath("$[?(@.id == " + tutor.getId() + ")].totalAvaliacoes").value(2))
                .andExpect(jsonPath("$[?(@.id == " + tutorPendente.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$[*].usuario.email").doesNotExist());
    }

    @Test
    void sessoesDisponiveisNaoExpoemEmailDoTutor() throws Exception {
        criarSessao(criarTutor("tutor@teste.com"), "DISPONIVEL", null);

        mockMvc.perform(get("/agendamento/disponiveis")
                .header("Authorization", tokenDe(criarEstudante("aluno@teste.com").getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tutor.usuario.nome").exists())
                .andExpect(jsonPath("$[0].tutor.usuario.email").doesNotExist())
                .andExpect(jsonPath("$[0].tutor.usuario.profiles").doesNotExist());
    }

    @Test
    void meRetornaOsIdsDosPerfisDoUsuario() throws Exception {
        Estudante aluno = criarEstudante("aluno@teste.com");

        mockMvc.perform(get("/usuario/me").header("Authorization", tokenDe(aluno.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("ESTUDANTE"))
                .andExpect(jsonPath("$.estudanteId").value(aluno.getId()))
                .andExpect(jsonPath("$.tutorId").isEmpty());
    }

    // ── Sessao expirada ou ausente: 401, para o frontend voltar ao login ─────

    @Test
    void requisicaoSemTokenOuComTokenInvalidoRetorna401() throws Exception {
        mockMvc.perform(get("/usuario/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/usuario/me").header("Authorization", "Bearer token-adulterado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }
}
