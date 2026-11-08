package br.com.tutoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.enums.ModalidadeEnum;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.support.IntegracaoTestBase;

// Tutoria presencial ou online: o tutor cola o link, o estudante escolhe
class VideoconferenciaTest extends IntegracaoTestBase {

    private static final String MEET = "https://meet.google.com/abc-defg-hij";
    private static final LocalDate DIA = LocalDate.now().plusDays(3);

    @Autowired private AgendamentoRepository agendamentoRepository;

    private Agendamento horario(Tutor tutor, String link) {
        return agendamentoRepository.save(
                Agendamento.disponibilizar(tutor, DIA, LocalTime.of(14, 0), null, link));
    }

    private void solicitar(Agendamento sessao, Estudante estudante, String modalidade, int statusEsperado)
            throws Exception {
        mockMvc.perform(put("/agendamento/" + sessao.getId() + "/solicitar")
                .header("Authorization", tokenDe(estudante.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("modalidade", modalidade))))
                .andExpect(status().is(statusEsperado));
    }

    // ── Regras da entidade ───────────────────────────────────────────────────

    @Test
    void linkPrecisaSerHttps() {
        Tutor tutor = new Tutor();
        assertThatThrownBy(() -> Agendamento.disponibilizar(tutor, DIA, LocalTime.NOON, null, "meet.google.com/x"))
                .hasMessageContaining("https://");
        assertThatThrownBy(() -> Agendamento.disponibilizar(tutor, DIA, LocalTime.NOON, null, "javascript:alert(1)"))
                .hasMessageContaining("https://");
        assertThat(Agendamento.disponibilizar(tutor, DIA, LocalTime.NOON, null, "  ").isOnlineDisponivel()).isFalse();
    }

    @Test
    void tutorNaoRemoveLinkDeSessaoReservadaOnline() {
        Agendamento sessao = Agendamento.disponibilizar(new Tutor(), DIA, LocalTime.NOON, null, MEET);
        sessao.agendar(new Estudante(), ModalidadeEnum.ONLINE);

        sessao.alterarLinkReuniao("https://zoom.us/j/123");
        assertThatThrownBy(() -> sessao.alterarLinkReuniao("")).hasMessageContaining("não o remova");
    }

    @Test
    void desistirLimpaAModalidade() {
        Agendamento sessao = Agendamento.disponibilizar(new Tutor(), DIA, LocalTime.NOON, null, MEET);
        sessao.agendar(new Estudante(), ModalidadeEnum.ONLINE);
        sessao.desistir();

        assertThat(sessao.getModalidade()).isNull();
    }

    // ── API ──────────────────────────────────────────────────────────────────

    @Test
    void estudanteLogadoVeOLinkNosHorariosDisponiveis() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");

        mockMvc.perform(post("/agendamento")
                .header("Authorization", tokenDe(tutor.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("data", DIA.toString(), "hora", "14:00", "linkReuniao", MEET))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onlineDisponivel").value(true))
                .andExpect(jsonPath("$.linkReuniao").value(MEET));

        mockMvc.perform(get("/agendamento/disponiveis")
                .header("Authorization", tokenDe(criarEstudante("ana@teste.com").getUsuario())))
                .andExpect(jsonPath("$[0].onlineDisponivel").value(true))
                .andExpect(jsonPath("$[0].linkReuniao").value(MEET));
    }

    @Test
    void semLoginNaoVeOsHorarios() throws Exception {
        horario(criarTutor("tutor@teste.com"), MEET);

        mockMvc.perform(get("/agendamento/disponiveis"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void linkInvalidoERecusado() throws Exception {
        mockMvc.perform(post("/agendamento")
                .header("Authorization", tokenDe(criarTutor("tutor@teste.com").getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("data", DIA.toString(), "hora", "14:00", "linkReuniao", "http://meet.x"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void estudanteEscolheOnlineSoSeHouverLink() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Agendamento semLink = horario(tutor, null);
        Estudante ana = criarEstudante("ana@teste.com");

        solicitar(semLink, ana, "ONLINE", 400);
        solicitar(semLink, ana, "QUALQUER", 400);
        solicitar(semLink, ana, "PRESENCIAL", 200);

        assertThat(agendamentoRepository.findById(semLink.getId()).orElseThrow().getModalidade())
                .isEqualTo(ModalidadeEnum.PRESENCIAL);
    }

    @Test
    void semCorpoATutoriaEPresencial() throws Exception {
        Agendamento sessao = horario(criarTutor("tutor@teste.com"), MEET);

        mockMvc.perform(put("/agendamento/" + sessao.getId() + "/solicitar")
                .header("Authorization", tokenDe(criarEstudante("ana@teste.com").getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modalidade").value("PRESENCIAL"));
    }

    @Test
    void linkSoELiberadoAoEstudanteDepoisDaConfirmacao() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Agendamento sessao = horario(tutor, MEET);
        Estudante ana = criarEstudante("ana@teste.com");
        String rota = "/agendamento/" + sessao.getId() + "/link-reuniao";

        // Ninguem fora da sessao ve o link
        mockMvc.perform(get(rota).header("Authorization", tokenDe(ana.getUsuario())))
                .andExpect(status().isForbidden());

        solicitar(sessao, ana, "ONLINE", 200);

        // Pendente: ainda nao
        mockMvc.perform(get(rota).header("Authorization", tokenDe(ana.getUsuario())))
                .andExpect(status().isForbidden());
        // O tutor sempre ve
        mockMvc.perform(get(rota).header("Authorization", tokenDe(tutor.getUsuario())))
                .andExpect(jsonPath("$.linkReuniao").value(MEET));

        mockMvc.perform(put("/agendamento/" + sessao.getId())
                .header("Authorization", tokenDe(tutor.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "CONFIRMADO"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(rota).header("Authorization", tokenDe(ana.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkReuniao").value(MEET));
        // Outro estudante continua sem acesso
        mockMvc.perform(get(rota).header("Authorization", tokenDe(criarEstudante("bia@teste.com").getUsuario())))
                .andExpect(status().isForbidden());
    }

    @Test
    void estudanteQueEscolheuPresencialNaoRecebeOLink() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Agendamento sessao = horario(tutor, MEET);
        Estudante ana = criarEstudante("ana@teste.com");
        solicitar(sessao, ana, "PRESENCIAL", 200);
        mockMvc.perform(put("/agendamento/" + sessao.getId())
                .header("Authorization", tokenDe(tutor.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "CONFIRMADO"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/agendamento/" + sessao.getId() + "/link-reuniao")
                .header("Authorization", tokenDe(ana.getUsuario())))
                .andExpect(status().isForbidden());
    }

    @Test
    void tutorAdicionaLinkDepoisDeCriarOHorario() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Agendamento sessao = horario(tutor, null);

        mockMvc.perform(put("/agendamento/" + sessao.getId())
                .header("Authorization", tokenDe(tutor.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("linkReuniao", MEET))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineDisponivel").value(true));
    }
}
