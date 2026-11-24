package br.com.tutoria.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.support.IntegracaoTestBase;

// O tutor escolhe a disciplina ao abrir o horario na agenda
class HorarioPorDisciplinaTest extends IntegracaoTestBase {

    private static final String DIA = LocalDate.now().plusDays(3).toString();

    private Long idDe(String disciplina) {
        return disciplinaService.buscarOuCriar(disciplina).getId();
    }

    private ResultActions abrir(Tutor tutor, String hora, Long disciplinaId) throws Exception {
        Map<String, Object> corpo = new HashMap<>(Map.of("data", DIA, "hora", hora));
        if (disciplinaId != null) {
            corpo.put("disciplinaId", disciplinaId);
        }
        return mockMvc.perform(post("/agendamento")
                .header("Authorization", tokenDe(tutor.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corpo)));
    }

    private long idCriado(ResultActions resposta) throws Exception {
        return objectMapper.readTree(resposta.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions solicitar(long horarioId, Estudante aluno, Long disciplinaId) throws Exception {
        Map<String, Object> corpo = new HashMap<>(Map.of("modalidade", "PRESENCIAL"));
        if (disciplinaId != null) {
            corpo.put("disciplinaId", disciplinaId);
        }
        return mockMvc.perform(put("/agendamento/" + horarioId + "/solicitar")
                .header("Authorization", tokenDe(aluno.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corpo)));
    }

    private ResultActions disponiveisEm(String disciplina) throws Exception {
        return mockMvc.perform(get("/agendamento/disponiveis").param("disciplina", disciplina)
                .header("Authorization", tokenAdmin()));
    }

    @Test
    void tutorComVariasDisciplinasEscolheAoAbrirOHorario() throws Exception {
        Tutor tutor = criarTutorDe("tutor@teste.com", "Algoritmos", "Banco de Dados");

        abrir(tutor, "14:00", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Escolha a disciplina deste horário"));
        abrir(tutor, "14:00", idDe("Matemática"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Você não atende essa disciplina"));

        abrir(tutor, "14:00", idDe("Banco de Dados"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disciplina.nome").value("Banco de Dados"));
    }

    @Test
    void tutorComUmaDisciplinaNaoPrecisaEscolher() throws Exception {
        abrir(criarTutor("tutor@teste.com"), "14:00", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disciplina.nome").value("Algoritmos"));
    }

    @Test
    void horarioApareceSoNaDisciplinaEscolhida() throws Exception {
        Tutor tutor = criarTutorDe("tutor@teste.com", "Algoritmos", "Banco de Dados");
        abrir(tutor, "14:00", idDe("Banco de Dados")).andExpect(status().isCreated());
        abrir(tutor, "16:00", idDe("Algoritmos")).andExpect(status().isCreated());

        disponiveisEm("Banco de Dados")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].hora").value("14:00:00"));
        disponiveisEm("algoritmos")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].hora").value("16:00:00"));
    }

    @Test
    void estudanteNaoTrocaADisciplinaDoHorario() throws Exception {
        Tutor tutor = criarTutorDe("tutor@teste.com", "Algoritmos", "Banco de Dados");
        long horario = idCriado(abrir(tutor, "14:00", idDe("Banco de Dados")));
        Estudante aluno = criarEstudante("aluno@teste.com");

        solicitar(horario, aluno, idDe("Algoritmos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este horário é de Banco de Dados"));

        // Sem informar, ou informando a mesma, vale a do horario
        solicitar(horario, aluno, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disciplina.nome").value("Banco de Dados"));
    }

    @Test
    void disciplinaQueOTutorDeixouDeAtenderTiraOHorarioDaBusca() throws Exception {
        Tutor tutor = criarTutorDe("tutor@teste.com", "Algoritmos", "Banco de Dados");
        long horario = idCriado(abrir(tutor, "14:00", idDe("Banco de Dados")));

        Long vinculo = disciplinaService.doTutor(tutor).stream()
                .filter(v -> v.disciplina().nome().equals("Banco de Dados")).findFirst().orElseThrow().id();
        mockMvc.perform(delete("/tutor/me/disciplinas/" + vinculo).header("Authorization", tokenDe(tutor.getUsuario())))
                .andExpect(status().isNoContent());

        disponiveisEm("Banco de Dados").andExpect(jsonPath("$", hasSize(0)));
        solicitar(horario, criarEstudante("aluno@teste.com"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O tutor não atende mais Banco de Dados"));
    }
}
