package br.com.tutoria.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.support.IntegracaoTestBase;

// Sino de notificacoes: avisos calculados a partir do estado atual
class NotificacaoTest extends IntegracaoTestBase {

    @Autowired private AgendamentoRepository agendamentoRepository;

    private ResultActions notificacoes(Usuario usuario) throws Exception {
        return mockMvc.perform(get("/notificacao").header("Authorization", tokenDe(usuario)));
    }

    private Agendamento solicitada(Tutor tutor, Estudante estudante, LocalDate data, int hora) {
        Agendamento sessao = Agendamento.disponibilizar(tutor, data, LocalTime.of(hora, 0), null);
        sessao.agendar(estudante);
        return agendamentoRepository.save(sessao);
    }

    @Test
    void semPendenciasNaoHaAvisos() throws Exception {
        Estudante estudante = criarEstudante("aluno@teste.com");

        notificacoes(estudante.getUsuario())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void tutorEAvisadoDeSolicitacaoEDeMensagemEOAvisoSomeQuandoResolve() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante estudante = criarEstudante("aluno@teste.com");
        Agendamento sessao = solicitada(tutor, estudante, LocalDate.now().plusDays(5), 14);

        String conversa = mockMvc.perform(post("/conversa")
                .header("Authorization", tokenDe(estudante.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("tutorId", tutor.getId()))))
                .andReturn().getResponse().getContentAsString();
        long conversaId = objectMapper.readTree(conversa).get("id").asLong();
        mockMvc.perform(post("/conversa/" + conversaId + "/mensagens")
                .header("Authorization", tokenDe(estudante.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("conteudo", "Oi, professor!"))))
                .andExpect(status().isCreated());

        notificacoes(tutor.getUsuario())
                .andExpect(jsonPath("$[*].tipo", hasItem("SOLICITACAO_PENDENTE")))
                .andExpect(jsonPath("$[*].tipo", hasItem("MENSAGEM")));

        sessao.confirmar();
        agendamentoRepository.save(sessao);
        mockMvc.perform(get("/conversa/" + conversaId + "/mensagens")
                .header("Authorization", tokenDe(tutor.getUsuario())));

        notificacoes(tutor.getUsuario())
                .andExpect(jsonPath("$[*].tipo", not(hasItem("SOLICITACAO_PENDENTE"))))
                .andExpect(jsonPath("$[*].tipo", not(hasItem("MENSAGEM"))));
    }

    @Test
    void estudanteRecebeLembreteDaTutoriaConfirmadaDeAmanha() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante estudante = criarEstudante("aluno@teste.com");
        Agendamento sessao = solicitada(tutor, estudante, LocalDate.now().plusDays(1), 10);
        sessao.confirmar();
        agendamentoRepository.save(sessao);

        notificacoes(estudante.getUsuario())
                .andExpect(jsonPath("$[0].tipo").value("LEMBRETE"))
                .andExpect(jsonPath("$[0].titulo").value("Tutoria confirmada amanhã às 10:00"));
    }

    @Test
    void coordenacaoEAvisadaDeCadastroPendente() throws Exception {
        criarUsuario("novo.tutor@teste.com", UsuarioStatusEnum.PENDENTE);

        mockMvc.perform(get("/notificacao").header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$[0].tipo").value("CADASTRO_PENDENTE"))
                .andExpect(jsonPath("$[0].link").value("aprovacoes.html"));
    }
}
