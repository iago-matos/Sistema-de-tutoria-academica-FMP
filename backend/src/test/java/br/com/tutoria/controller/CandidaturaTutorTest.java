package br.com.tutoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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

import com.fasterxml.jackson.databind.JsonNode;

import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.support.IntegracaoTestBase;

// Estudante que se candidata a tutor com a mesma conta (guia "Candidatar-se")
class CandidaturaTutorTest extends IntegracaoTestBase {

    private static final LocalDate DAQUI_A_TRES_DIAS = LocalDate.now().plusDays(3);

    @Autowired private AgendamentoRepository agendamentoRepository;

    private Long disciplinaId(String nome) {
        return disciplinaService.buscarOuCriar(nome).getId();
    }

    private ResultActions candidatar(Usuario usuario, Long disciplinaId) throws Exception {
        return mockMvc.perform(post("/estudante/me/candidatura")
                .header("Authorization", tokenDe(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("disciplinaId", disciplinaId))));
    }

    private ResultActions situacao(Usuario usuario) throws Exception {
        return mockMvc.perform(get("/estudante/me/candidatura").header("Authorization", tokenDe(usuario)));
    }

    private ResultActions me(Usuario usuario) throws Exception {
        return mockMvc.perform(get("/usuario/me").header("Authorization", tokenDe(usuario)));
    }

    private ResultActions criarHorario(Usuario usuario, String hora) throws Exception {
        return mockMvc.perform(post("/agendamento")
                .header("Authorization", tokenDe(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("data", DAQUI_A_TRES_DIAS.toString(), "hora", hora))));
    }

    // Id do pedido pendente do usuario, como a coordenacao ve em Aprovacoes
    private JsonNode pedidoDe(Usuario usuario) throws Exception {
        JsonNode pedidos = objectMapper.readTree(mockMvc.perform(get("/disciplina/pedidos")
                .header("Authorization", tokenAdmin()))
                .andReturn().getResponse().getContentAsString());
        for (JsonNode pedido : pedidos) {
            if (pedido.get("tutorNome").asText().equals(usuario.getNome())) {
                return pedido;
            }
        }
        throw new AssertionError("Pedido nao encontrado para " + usuario.getNome());
    }

    private void decidir(Usuario usuario, String decisao) throws Exception {
        mockMvc.perform(put("/disciplina/pedidos/" + pedidoDe(usuario).get("id").asLong() + "/" + decisao)
                .header("Authorization", tokenAdmin()))
                .andExpect(status().isOk());
    }

    // Estudante candidato aprovado: o mesmo usuario com os dois perfis
    private Usuario estudanteTutor(String email) throws Exception {
        Usuario usuario = criarEstudante(email).getUsuario();
        candidatar(usuario, disciplinaId("Banco de Dados")).andExpect(status().isCreated());
        decidir(usuario, "aprovar");
        return usuario;
    }

    @Test
    void candidaturaPendenteNaoLiberaAAreaDoTutor() throws Exception {
        Usuario aluno = criarEstudante("aluno@teste.com").getUsuario();

        situacao(aluno).andExpect(jsonPath("$.situacao").value("NENHUMA"));
        candidatar(aluno, disciplinaId("Banco de Dados"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacao").value("PENDENTE"))
                .andExpect(jsonPath("$.disciplina.nome").value("Banco de Dados"));

        // Continua estudante: login, area e tipo nao mudam
        me(aluno)
                .andExpect(jsonPath("$.tipo").value("ESTUDANTE"))
                .andExpect(jsonPath("$.perfis", contains("ESTUDANTE")));
        login("aluno@teste.com", SENHA_PADRAO).andExpect(status().isOk());

        // Nao aparece na busca nem abre horarios
        mockMvc.perform(get("/tutor").header("Authorization", tokenDe(aluno)))
                .andExpect(jsonPath("$[*].usuario.nome", not(hasItem(aluno.getNome()))));
        criarHorario(aluno, "14:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("candidatura")));

        // A coordenacao ve o pedido marcado como candidatura
        assertThat(pedidoDe(aluno).get("candidatura").asBoolean()).isTrue();

        // Nao pode mandar outra enquanto esta pendente
        candidatar(aluno, disciplinaId("Algoritmos")).andExpect(status().isBadRequest());
    }

    @Test
    void aprovadoFicaComOsDoisPerfisNaMesmaConta() throws Exception {
        Usuario usuario = estudanteTutor("monitor@teste.com");

        situacao(usuario).andExpect(jsonPath("$.situacao").value("APROVADA"));
        me(usuario)
                .andExpect(jsonPath("$.perfis", containsInAnyOrder("ESTUDANTE", "TUTOR")))
                .andExpect(jsonPath("$.estudanteId").isNumber())
                .andExpect(jsonPath("$.tutorId").isNumber());

        mockMvc.perform(get("/tutor").header("Authorization", tokenDe(usuario)))
                .andExpect(jsonPath("$[*].usuario.nome", hasItem(usuario.getNome())));
        criarHorario(usuario, "14:00").andExpect(status().isCreated());
        candidatar(usuario, disciplinaId("Algoritmos")).andExpect(status().isBadRequest());

        // A tutoria de outro pedido de disciplina (ja tutor) nao e candidatura
        mockMvc.perform(post("/tutor/me/disciplinas")
                .header("Authorization", tokenDe(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("disciplinaId", disciplinaId("Algoritmos")))))
                .andExpect(status().isCreated());
        assertThat(pedidoDe(usuario).get("candidatura").asBoolean()).isFalse();
    }

    @Test
    void recusadaPodeSerEnviadaDeNovoOuCancelada() throws Exception {
        Usuario aluno = criarEstudante("aluno@teste.com").getUsuario();
        candidatar(aluno, disciplinaId("Banco de Dados")).andExpect(status().isCreated());
        decidir(aluno, "recusar");

        situacao(aluno).andExpect(jsonPath("$.situacao").value("RECUSADA"));
        me(aluno).andExpect(jsonPath("$.perfis", contains("ESTUDANTE")));

        candidatar(aluno, disciplinaId("Algoritmos"))
                .andExpect(jsonPath("$.situacao").value("PENDENTE"))
                .andExpect(jsonPath("$.disciplina.nome").value("Algoritmos"));

        mockMvc.perform(delete("/estudante/me/candidatura").header("Authorization", tokenDe(aluno)))
                .andExpect(status().isNoContent());
        situacao(aluno).andExpect(jsonPath("$.situacao").value("RECUSADA"));
        mockMvc.perform(delete("/estudante/me/candidatura").header("Authorization", tokenDe(aluno)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soEstudantesSeCandidatam() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");

        candidatar(tutor.getUsuario(), disciplinaId("Banco de Dados")).andExpect(status().isForbidden());
        mockMvc.perform(get("/estudante/me/candidatura").header("Authorization", tokenAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void naoSolicitaOProprioHorario() throws Exception {
        Usuario usuario = estudanteTutor("monitor@teste.com");
        String corpo = criarHorario(usuario, "14:00").andReturn().getResponse().getContentAsString();
        long horario = objectMapper.readTree(corpo).get("id").asLong();

        mockMvc.perform(put("/agendamento/" + horario + "/solicitar")
                .header("Authorization", tokenDe(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("modalidade", "PRESENCIAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("própria agenda")));
    }

    @Test
    void naoDaTutoriaEAssisteAOutraNoMesmoHorario() throws Exception {
        Usuario usuario = estudanteTutor("monitor@teste.com");
        Estudante comoEstudante = estudanteRepository.findByUsuario_Id(usuario.getId()).orElseThrow();
        Tutor outroTutor = criarTutor("prof@teste.com");

        // Ja tem horario como tutor as 14h: nao pode reservar tutoria as 14h
        criarHorario(usuario, "14:00").andExpect(status().isCreated());
        Agendamento aula = agendamentoRepository.save(
                Agendamento.disponibilizar(outroTutor, DAQUI_A_TRES_DIAS, LocalTime.of(14, 0), null));
        mockMvc.perform(put("/agendamento/" + aula.getId() + "/solicitar")
                .header("Authorization", tokenDe(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("modalidade", "PRESENCIAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("agenda de tutor")));

        // Tem tutoria marcada como estudante as 16h: nao abre horario de tutor as 16h
        Agendamento reservada = Agendamento.disponibilizar(outroTutor, DAQUI_A_TRES_DIAS, LocalTime.of(16, 0), null);
        reservada.agendar(comoEstudante);
        agendamentoRepository.save(reservada);
        criarHorario(usuario, "16:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("como estudante")));
    }

    @Test
    void cadaAreaMostraAAgendaDoProprioPapel() throws Exception {
        Usuario usuario = estudanteTutor("monitor@teste.com");
        Estudante comoEstudante = estudanteRepository.findByUsuario_Id(usuario.getId()).orElseThrow();
        Tutor outroTutor = criarTutor("prof@teste.com");

        criarHorario(usuario, "10:00").andExpect(status().isCreated());
        Agendamento reservada = Agendamento.disponibilizar(outroTutor, DAQUI_A_TRES_DIAS, LocalTime.of(16, 0), null);
        reservada.agendar(comoEstudante);
        agendamentoRepository.save(reservada);

        mockMvc.perform(get("/agendamento/minha-agenda").param("perfil", "TUTOR")
                .header("Authorization", tokenDe(usuario)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].hora").value("10:00:00"));
        mockMvc.perform(get("/agendamento/minha-agenda").param("perfil", "ESTUDANTE")
                .header("Authorization", tokenDe(usuario)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].hora").value("16:00:00"));
    }
}
