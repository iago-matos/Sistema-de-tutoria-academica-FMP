package br.com.tutoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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
import org.springframework.test.web.servlet.ResultActions;

import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.support.IntegracaoTestBase;

// Lista de disciplinas da coordenacao, disciplinas do tutor (com aprovacao)
// e a disciplina escolhida pelo estudante ao solicitar uma tutoria
class DisciplinaTest extends IntegracaoTestBase {

    private static final LocalDate DAQUI_A_TRES_DIAS = LocalDate.now().plusDays(3);

    @Autowired private AgendamentoRepository agendamentoRepository;

    private Long idDe(String nome) {
        return disciplinaService.buscarPorNome(nome).orElseThrow().getId();
    }

    private ResultActions pedir(Tutor tutor, Long disciplinaId) throws Exception {
        return mockMvc.perform(post("/tutor/me/disciplinas")
                .header("Authorization", tokenDe(tutor.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("disciplinaId", disciplinaId))));
    }

    private Long pedidoPendente(Tutor tutor, String disciplina) throws Exception {
        pedir(tutor, idDe(disciplina)).andExpect(status().isCreated());
        return disciplinaService.doTutor(tutor).stream()
                .filter(v -> v.disciplina().nome().equals(disciplina))
                .findFirst().orElseThrow().id();
    }

    private ResultActions solicitar(Agendamento horario, Estudante estudante, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(put("/agendamento/" + horario.getId() + "/solicitar")
                .header("Authorization", tokenDe(estudante.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corpo)));
    }

    private Agendamento horarioLivre(Tutor tutor) {
        return agendamentoRepository.save(
                Agendamento.disponibilizar(tutor, DAQUI_A_TRES_DIAS, LocalTime.of(10, 0), null));
    }

    // ── Lista da coordenacao ─────────────────────────────────────────────────

    @Test
    void listaEPublicaEmOrdemAlfabetica() throws Exception {
        mockMvc.perform(get("/disciplina"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Algoritmos"))
                .andExpect(jsonPath("$[*].nome").value(hasItem("Banco de Dados")))
                .andExpect(jsonPath("$[*].nome").value(hasItem("Matemática")));
    }

    @Test
    void adminCriaRenomeiaEExcluiDisciplinaSemUso() throws Exception {
        String id = mockMvc.perform(post("/disciplina").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "  Redes   de Computadores "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Redes de Computadores"))
                .andReturn().getResponse().getContentAsString();
        id = objectMapper.readTree(id).get("id").asText();

        mockMvc.perform(put("/disciplina/" + id).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Redes"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Redes"));

        mockMvc.perform(delete("/disciplina/" + id).header("Authorization", tokenAdmin()))
                .andExpect(status().isNoContent());
        assertThat(disciplinaService.buscarPorNome("Redes")).isEmpty();
    }

    // "matematica" e "Matemática" seriam duas disciplinas na busca do estudante
    @Test
    void naoCriaDisciplinaRepetidaMesmoSemAcentoOuMaiusculas() throws Exception {
        mockMvc.perform(post("/disciplina").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "MATEMATICA"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Matemática")));
    }

    @Test
    void naoExcluiDisciplinaQueUmTutorUsa() throws Exception {
        criarTutor("tutor@teste.com"); // Algoritmos

        mockMvc.perform(delete("/disciplina/" + idDe("Algoritmos")).header("Authorization", tokenAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("renomeie")));
    }

    @Test
    void soAdminMexeNaListaENosPedidos() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        String token = tokenDe(tutor.getUsuario());

        mockMvc.perform(post("/disciplina").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("nome", "Física"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/disciplina/pedidos").header("Authorization", token))
                .andExpect(status().isForbidden());
        Long pedido = pedidoPendente(tutor, "Matemática");
        mockMvc.perform(put("/disciplina/pedidos/" + pedido + "/aprovar").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    // ── Disciplinas do tutor ─────────────────────────────────────────────────

    @Test
    void disciplinaNovaSoApareceNaBuscaDepoisDaAprovacao() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com"); // Algoritmos, ativa
        horarioLivre(tutor);
        String tokenAluno = tokenDe(criarEstudante("aluno@teste.com").getUsuario());

        Long pedido = pedidoPendente(tutor, "Matemática");

        mockMvc.perform(get("/tutor/me/disciplinas").header("Authorization", tokenDe(tutor.getUsuario())))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.disciplina.nome == 'Matemática')].status").value("PENDENTE"));
        mockMvc.perform(get("/agendamento/disponiveis").param("disciplina", "Matemática").header("Authorization", tokenAluno))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/disciplina/pedidos").header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$[?(@.id == " + pedido + ")].tutorNome").value("Usuario tutor@teste.com"));

        mockMvc.perform(put("/disciplina/pedidos/" + pedido + "/aprovar").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVA"));

        // O mesmo horario livre aparece nas duas disciplinas do tutor
        mockMvc.perform(get("/agendamento/disponiveis").param("disciplina", "matemática").header("Authorization", tokenAluno))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/agendamento/disponiveis").param("disciplina", "Algoritmos").header("Authorization", tokenAluno))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/tutor").header("Authorization", tokenAluno))
                .andExpect(jsonPath("$[?(@.id == " + tutor.getId() + ")].disciplinas[*].nome")
                        .value(org.hamcrest.Matchers.contains("Algoritmos", "Matemática")));
    }

    @Test
    void recusaFicaVisivelParaOTutorQuePodePedirDeNovo() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Long pedido = pedidoPendente(tutor, "Matemática");

        mockMvc.perform(put("/disciplina/pedidos/" + pedido + "/recusar").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADA"));
        mockMvc.perform(put("/disciplina/pedidos/" + pedido + "/aprovar").header("Authorization", tokenAdmin()))
                .andExpect(status().isBadRequest());

        pedir(tutor, idDe("Matemática"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    void naoPedeDisciplinaQueJaTemOuJaPediu() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");

        pedir(tutor, idDe("Algoritmos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("já está entre")));
        pedidoPendente(tutor, "Matemática");
        pedir(tutor, idDe("Matemática"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("aguardando")));
    }

    @Test
    void tutorPrecisaManterUmaDisciplinaAtiva() throws Exception {
        Tutor tutor = criarTutorDe("tutor@teste.com", "Algoritmos", "Banco de Dados");
        String token = tokenDe(tutor.getUsuario());
        Long algoritmos = disciplinaService.doTutor(tutor).stream()
                .filter(v -> v.disciplina().nome().equals("Algoritmos")).findFirst().orElseThrow().id();
        Long bancoDeDados = disciplinaService.doTutor(tutor).stream()
                .filter(v -> v.disciplina().nome().equals("Banco de Dados")).findFirst().orElseThrow().id();

        mockMvc.perform(delete("/tutor/me/disciplinas/" + algoritmos).header("Authorization", token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/tutor/me/disciplinas/" + bancoDeDados).header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("ao menos uma")));
    }

    @Test
    void tutorNaoRemoveDisciplinaDeOutroTutor() throws Exception {
        Tutor dono = criarTutor("dono@teste.com");
        Tutor outro = criarTutor("outro@teste.com");
        Long vinculoDoDono = disciplinaService.doTutor(dono).get(0).id();

        mockMvc.perform(delete("/tutor/me/disciplinas/" + vinculoDoDono)
                        .header("Authorization", tokenDe(outro.getUsuario())))
                .andExpect(status().isNotFound());
        assertThat(disciplinaService.doTutor(dono)).hasSize(1);
    }

    @Test
    void estudanteNaoTemGuiaDeDisciplinasDeTutor() throws Exception {
        mockMvc.perform(get("/tutor/me/disciplinas")
                        .header("Authorization", tokenDe(criarEstudante("aluno@teste.com").getUsuario())))
                .andExpect(status().isForbidden());
    }

    @Test
    void cadastroPendenteDeTutorMostraADisciplinaParaACoordenacao() throws Exception {
        Tutor pendente = criarTutorDe("novo-tutor@teste.com", "Banco de Dados");
        pendente.getUsuario().setStatus(UsuarioStatusEnum.PENDENTE);
        usuarioRepository.save(pendente.getUsuario());

        mockMvc.perform(get("/usuario/pendentes").header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$[?(@.email == 'novo-tutor@teste.com')].disciplinas[0]").value("Banco de Dados"));
    }

    // ── Disciplina da tutoria solicitada ─────────────────────────────────────

    @Test
    void tutorComUmaDisciplinaNaoExigeEscolha() throws Exception {
        Agendamento horario = horarioLivre(criarTutor("tutor@teste.com"));

        solicitar(horario, criarEstudante("aluno@teste.com"), Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disciplina.nome").value("Algoritmos"));
    }

    @Test
    void tutorComVariasDisciplinasExigeEscolhaDeUmaQueEleAtende() throws Exception {
        Tutor tutor = criarTutorDe("tutor@teste.com", "Algoritmos", "Banco de Dados");
        Agendamento horario = horarioLivre(tutor);
        Estudante aluno = criarEstudante("aluno@teste.com");

        solicitar(horario, aluno, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Escolha a disciplina da tutoria"));
        solicitar(horario, aluno, Map.of("disciplinaId", idDe("Matemática")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este tutor não atende essa disciplina"));

        solicitar(horario, aluno, Map.of("modalidade", "PRESENCIAL", "disciplinaId", idDe("Banco de Dados")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.disciplina.nome").value("Banco de Dados"));
    }

    @Test
    void desistirLiberaOHorarioSemDisciplina() throws Exception {
        Agendamento horario = horarioLivre(criarTutor("tutor@teste.com"));
        Estudante aluno = criarEstudante("aluno@teste.com");
        solicitar(horario, aluno, Map.of()).andExpect(status().isOk());

        mockMvc.perform(put("/agendamento/" + horario.getId() + "/cancelar")
                        .header("Authorization", tokenDe(aluno.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPONIVEL"))
                .andExpect(jsonPath("$.disciplina").doesNotExist());
    }

    @Test
    void disciplinaPendenteNaoPodeSerEscolhidaPeloEstudante() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        pedidoPendente(tutor, "Matemática");

        solicitar(horarioLivre(tutor), criarEstudante("aluno@teste.com"), Map.of("disciplinaId", idDe("Matemática")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(not(containsString("Escolha"))));
    }
}
