package br.com.tutoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Avaliacao;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.enums.AgendamentoStatusEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.repository.AvaliacaoRepository;
import br.com.tutoria.support.IntegracaoTestBase;

class AgendamentoFluxoTest extends IntegracaoTestBase {

    private static final LocalDate DAQUI_A_TRES_DIAS = LocalDate.now().plusDays(3);

    @Autowired private AgendamentoRepository agendamentoRepository;
    @Autowired private AvaliacaoRepository avaliacaoRepository;

    private Agendamento horarioLivre(Tutor tutor, int hora) {
        return agendamentoRepository.save(
                Agendamento.disponibilizar(tutor, DAQUI_A_TRES_DIAS, LocalTime.of(hora, 0), null));
    }

    private Tutor tutorDe(String email, String disciplina) {
        return criarTutorDe(email, disciplina);
    }

    private void criarHorario(Tutor tutor, String data, String hora, int statusEsperado) throws Exception {
        mockMvc.perform(post("/agendamento")
                .header("Authorization", tokenDe(tutor.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("data", data, "hora", hora))))
                .andExpect(status().is(statusEsperado));
    }

    // ── Tutor ────────────────────────────────────────────────────────────────

    @Test
    void horarioCriadoInformaDuracaoDeUmaHora() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");

        mockMvc.perform(post("/agendamento")
                .header("Authorization", tokenDe(tutor.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "data", DAQUI_A_TRES_DIAS.toString(),
                        "hora", "14:00",
                        "observacoes", "Revisão para a P1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DISPONIVEL"))
                .andExpect(jsonPath("$.duracaoMinutos").value(60))
                .andExpect(jsonPath("$.horaFim").value("15:00:00"))
                .andExpect(jsonPath("$.observacoes").value("Revisão para a P1"));
    }

    @Test
    void tutorNaoCriaHorarioNoPassadoNemSobrepostoAOutro() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        String dia = DAQUI_A_TRES_DIAS.toString();

        criarHorario(tutor, LocalDate.now().minusDays(1).toString(), "14:00", 400);
        criarHorario(tutor, dia, "14:00", 201);
        criarHorario(tutor, dia, "14:30", 400); // cairia dentro da sessao das 14h
        criarHorario(tutor, dia, "15:00", 201); // comeca quando a anterior termina
    }

    @Test
    void horarioCanceladoLiberaAAgendaDoTutor() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Agendamento sessao = horarioLivre(tutor, 14);

        mockMvc.perform(put("/agendamento/" + sessao.getId() + "/cancelar")
                .header("Authorization", tokenDe(tutor.getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        criarHorario(tutor, DAQUI_A_TRES_DIAS.toString(), "14:00", 201);
    }

    @Test
    void statusSoMudaPorTransicoesValidas() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Agendamento livre = horarioLivre(tutor, 14);
        String token = tokenDe(tutor.getUsuario());

        // Ninguem solicitou ainda: nao ha o que confirmar
        mockMvc.perform(put("/agendamento/" + livre.getId())
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "CONFIRMADO"))))
                .andExpect(status().isBadRequest());

        // Status que so o sistema define
        mockMvc.perform(put("/agendamento/" + livre.getId())
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "PENDENTE"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/agendamento/" + livre.getId())
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "QUALQUER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Status inválido. Use CONFIRMADO, CANCELADO ou REALIZADO"));
    }

    // ── Estudante ────────────────────────────────────────────────────────────

    @Test
    void segundoEstudanteNaoPegaHorarioJaSolicitado() throws Exception {
        Agendamento sessao = horarioLivre(criarTutor("tutor@teste.com"), 14);

        mockMvc.perform(put("/agendamento/" + sessao.getId() + "/solicitar")
                .header("Authorization", tokenDe(criarEstudante("ana@teste.com").getUsuario())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/agendamento/" + sessao.getId() + "/solicitar")
                .header("Authorization", tokenDe(criarEstudante("bia@teste.com").getUsuario())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este horário não está mais disponível"));
    }

    @Test
    void estudanteNaoMarcaDuasTutoriasNoMesmoHorario() throws Exception {
        Agendamento calculo = horarioLivre(tutorDe("t1@teste.com", "Cálculo"), 14);
        Agendamento fisica = horarioLivre(tutorDe("t2@teste.com", "Física"), 14);
        String token = tokenDe(criarEstudante("ana@teste.com").getUsuario());

        mockMvc.perform(put("/agendamento/" + calculo.getId() + "/solicitar").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(put("/agendamento/" + fisica.getId() + "/solicitar").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Você já tem uma tutoria marcada nesse horário"));
    }

    @Test
    void estudanteDesisteEOHorarioVoltaParaOsOutros() throws Exception {
        Agendamento sessao = horarioLivre(criarTutor("tutor@teste.com"), 14);
        String tokenAna = tokenDe(criarEstudante("ana@teste.com").getUsuario());

        mockMvc.perform(put("/agendamento/" + sessao.getId() + "/solicitar").header("Authorization", tokenAna))
                .andExpect(status().isOk());

        mockMvc.perform(put("/agendamento/" + sessao.getId() + "/cancelar").header("Authorization", tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPONIVEL"))
                .andExpect(jsonPath("$.estudante").isEmpty());

        mockMvc.perform(put("/agendamento/" + sessao.getId() + "/solicitar")
                .header("Authorization", tokenDe(criarEstudante("bia@teste.com").getUsuario())))
                .andExpect(status().isOk());
    }

    @Test
    void estudanteNaoCancelaNoDiaDaTutoria() throws Exception {
        Estudante ana = criarEstudante("ana@teste.com");
        // Mesmo fuso do relogio da aplicacao (RelogioConfig)
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Agendamento deHoje = Agendamento.disponibilizar(criarTutor("tutor@teste.com"), hoje, LocalTime.of(23, 0), null);
        deHoje.agendar(ana);
        agendamentoRepository.save(deHoje);

        mockMvc.perform(put("/agendamento/" + deHoje.getId() + "/cancelar")
                .header("Authorization", tokenDe(ana.getUsuario())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O cancelamento só pode ser feito até o dia anterior à tutoria"));

        assertThat(agendamentoRepository.findById(deHoje.getId()).orElseThrow().getStatus())
                .isEqualTo(AgendamentoStatusEnum.PENDENTE);
    }

    @Test
    void quemNaoParticipaNaoCancela() throws Exception {
        Estudante ana = criarEstudante("ana@teste.com");
        Agendamento sessao = horarioLivre(criarTutor("tutor@teste.com"), 14);
        sessao.agendar(ana);
        agendamentoRepository.save(sessao);

        mockMvc.perform(put("/agendamento/" + sessao.getId() + "/cancelar")
                .header("Authorization", tokenDe(criarEstudante("bia@teste.com").getUsuario())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/agendamento/" + sessao.getId() + "/cancelar")
                .header("Authorization", tokenDe(criarTutor("outro@teste.com").getUsuario())))
                .andExpect(status().isForbidden());
    }

    @Test
    void horariosDisponiveisFiltradosPorDisciplinaSemDiferenciarMaiusculas() throws Exception {
        Tutor calculo = tutorDe("t1@teste.com", "Cálculo I");
        Tutor redes = tutorDe("t2@teste.com", "Redes");
        horarioLivre(calculo, 9);
        horarioLivre(calculo, 10);
        horarioLivre(redes, 9);
        agendamentoRepository.save(
                Agendamento.disponibilizar(calculo, LocalDate.now().minusDays(1), LocalTime.of(9, 0), null));
        String token = tokenDe(criarEstudante("ana@teste.com").getUsuario());

        mockMvc.perform(get("/agendamento/disponiveis").param("disciplina", "  cálculo i ")
                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // o horario de ontem nao aparece
                .andExpect(jsonPath("$[*].tutor.disciplinas[0].nome").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("Cálculo I"))));

        mockMvc.perform(get("/agendamento/disponiveis").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void minhaAgendaMostraSoAsSessoesDeQuemEstaLogado() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante ana = criarEstudante("ana@teste.com");
        Agendamento daAna = horarioLivre(tutor, 9);
        daAna.agendar(ana);
        agendamentoRepository.save(daAna);
        horarioLivre(tutor, 10);

        mockMvc.perform(get("/agendamento/minha-agenda").header("Authorization", tokenDe(ana.getUsuario())))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(daAna.getId()));

        mockMvc.perform(get("/agendamento/minha-agenda").header("Authorization", tokenDe(tutor.getUsuario())))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ── Coordenacao: inativacao de tutor e estudante ─────────────────────────

    private Avaliacao avaliar(Tutor tutor, Estudante estudante) {
        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setTutor(tutor);
        avaliacao.setEstudante(estudante);
        avaliacao.setNota(5);
        avaliacao.setDataAvaliacao(LocalDate.now());
        avaliacao.setHoraAvaliacao(LocalTime.NOON);
        return avaliacaoRepository.save(avaliacao);
    }

    private void inativar(Long usuarioId) throws Exception {
        mockMvc.perform(put("/usuario/" + usuarioId + "/inativar").header("Authorization", tokenAdmin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void inativarTutorCancelaHorariosFuturosEGuardaOHistorico() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante ana = criarEstudante("ana@teste.com");
        Agendamento reservada = horarioLivre(tutor, 9);
        reservada.agendar(ana);
        agendamentoRepository.save(reservada);
        Agendamento livre = horarioLivre(tutor, 10);
        Agendamento passada = Agendamento.disponibilizar(tutor, LocalDate.now().minusDays(7), LocalTime.of(9, 0), null);
        passada.agendar(ana);
        passada.confirmar();
        passada.concluir();
        agendamentoRepository.save(passada);
        avaliar(tutor, ana);
        Long usuarioId = tutor.getUsuario().getId();

        inativar(usuarioId);

        assertThat(usuarioRepository.findById(usuarioId).orElseThrow().getStatus())
                .isEqualTo(UsuarioStatusEnum.INATIVO);
        assertThat(tutorRepository.findById(tutor.getId())).isPresent();
        assertThat(agendamentoRepository.findById(reservada.getId()).orElseThrow().getStatus())
                .isEqualTo(AgendamentoStatusEnum.CANCELADO);
        assertThat(agendamentoRepository.findById(livre.getId()).orElseThrow().getStatus())
                .isEqualTo(AgendamentoStatusEnum.CANCELADO);
        assertThat(agendamentoRepository.findById(passada.getId()).orElseThrow().getStatus())
                .isEqualTo(AgendamentoStatusEnum.REALIZADO);
        assertThat(avaliacaoRepository.findByTutor_Id(tutor.getId())).hasSize(1);

        // Some da busca de tutores e nao faz mais login
        mockMvc.perform(get("/tutor").header("Authorization", tokenDe(ana.getUsuario())))
                .andExpect(jsonPath("$[?(@.id == " + tutor.getId() + ")]").isEmpty());
        login("tutor@teste.com", SENHA_PADRAO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("desativada")));
    }

    @Test
    void inativarEstudanteDevolveAsReservasFuturasEGuardaOHistorico() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante ana = criarEstudante("ana@teste.com");
        Agendamento reserva = horarioLivre(tutor, 9);
        reserva.agendar(ana);
        reserva.confirmar();
        agendamentoRepository.save(reserva);
        avaliar(tutor, ana);
        String tokenAntigo = tokenDe(ana.getUsuario());

        inativar(ana.getUsuario().getId());

        Agendamento depois = agendamentoRepository.findById(reserva.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(AgendamentoStatusEnum.DISPONIVEL);
        assertThat(depois.getEstudante()).isNull();
        assertThat(estudanteRepository.findById(ana.getId())).isPresent();
        assertThat(avaliacaoRepository.findByEstudante_Id(ana.getId())).hasSize(1);

        // O token emitido antes da inativacao deixa de valer
        mockMvc.perform(get("/usuario/me").header("Authorization", tokenAntigo))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void reativarDevolveOAcesso() throws Exception {
        Estudante ana = criarEstudante("ana@teste.com");
        Long usuarioId = ana.getUsuario().getId();
        inativar(usuarioId);

        mockMvc.perform(put("/usuario/" + usuarioId + "/reativar").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVO"));
        login("ana@teste.com", SENHA_PADRAO).andExpect(status().isOk());

        // Reativar so vale para contas inativas
        mockMvc.perform(put("/usuario/" + usuarioId + "/reativar").header("Authorization", tokenAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soAdminInativaENaoInativaAdminNemContaJaInativa() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Long tutorUsuarioId = tutor.getUsuario().getId();
        Long adminId = usuarioRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();

        mockMvc.perform(put("/usuario/" + tutorUsuarioId + "/inativar")
                .header("Authorization", tokenDe(criarEstudante("ana@teste.com").getUsuario())))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/usuario/" + adminId + "/inativar").header("Authorization", tokenAdmin()))
                .andExpect(status().isBadRequest());

        inativar(tutorUsuarioId);
        mockMvc.perform(put("/usuario/" + tutorUsuarioId + "/inativar").header("Authorization", tokenAdmin()))
                .andExpect(status().isBadRequest());

        // Nao existe mais exclusao definitiva
        mockMvc.perform(delete("/usuario/" + tutorUsuarioId).header("Authorization", tokenAdmin()))
                .andExpect(status().is4xxClientError());
        assertThat(usuarioRepository.findById(tutorUsuarioId)).isPresent();
    }

    // ── Coordenacao: apagar dados pessoais (pedido feito na secretaria) ──────

    private void anonimizar(Long usuarioId, int statusEsperado) throws Exception {
        mockMvc.perform(put("/usuario/" + usuarioId + "/anonimizar").header("Authorization", tokenAdmin()))
                .andExpect(status().is(statusEsperado));
    }

    @Test
    void apagarDadosTiraAIdentidadeMasGuardaOHistorico() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante ana = criarEstudante("ana@teste.com");
        Agendamento passada = Agendamento.disponibilizar(tutor, LocalDate.now().minusDays(7), LocalTime.of(9, 0), null);
        passada.agendar(ana);
        passada.confirmar();
        passada.concluir();
        agendamentoRepository.save(passada);
        avaliar(tutor, ana);
        Long usuarioId = ana.getUsuario().getId();

        inativar(usuarioId);
        anonimizar(usuarioId, 200);

        var anonimizado = usuarioRepository.findById(usuarioId).orElseThrow();
        assertThat(anonimizado.getStatus()).isEqualTo(UsuarioStatusEnum.ANONIMIZADO);
        assertThat(anonimizado.getNome()).isEqualTo("Usuário removido");
        assertThat(anonimizado.getEmail()).doesNotContain("ana").endsWith(".invalid");
        assertThat(agendamentoRepository.findById(passada.getId()).orElseThrow().getEstudante().getId())
                .isEqualTo(ana.getId());
        assertThat(avaliacaoRepository.findByEstudante_Id(ana.getId())).hasSize(1);

        // Sem volta: nao faz login, nao reativa, nao edita
        login("ana@teste.com", SENHA_PADRAO).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/usuario/" + usuarioId + "/reativar").header("Authorization", tokenAdmin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/usuario/" + usuarioId).header("Authorization", tokenAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nome", "Ana"))))
                .andExpect(status().isBadRequest());

        // O e-mail original fica livre para um cadastro novo
        mockMvc.perform(post("/usuario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nome", "Ana", "email", "ana@teste.com", "senha", SENHA_PADRAO,
                        "tipo", "ESTUDANTE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void soApagaDadosDeContaInativaESoOAdminApaga() throws Exception {
        Estudante ana = criarEstudante("ana@teste.com");
        Long usuarioId = ana.getUsuario().getId();

        anonimizar(usuarioId, 400); // ainda ativa: precisa inativar antes

        inativar(usuarioId);
        mockMvc.perform(put("/usuario/" + usuarioId + "/anonimizar")
                .header("Authorization", tokenDe(criarTutor("tutor@teste.com").getUsuario())))
                .andExpect(status().isForbidden());

        anonimizar(usuarioId, 200);
        anonimizar(usuarioId, 400); // so uma vez
    }

    @Test
    void listaDeUsuariosInformaOTipoDeCadaUm() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante ana = criarEstudante("ana@teste.com");

        mockMvc.perform(get("/usuario").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + tutor.getUsuario().getId() + ")].tipo").value("TUTOR"))
                .andExpect(jsonPath("$[?(@.id == " + ana.getUsuario().getId() + ")].tipo").value("ESTUDANTE"))
                .andExpect(jsonPath("$[?(@.email == '" + ADMIN_EMAIL + "')].tipo").value("ADMIN"))
                .andExpect(jsonPath("$[*].senha").doesNotExist());
    }
}
