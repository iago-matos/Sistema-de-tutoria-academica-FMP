package br.com.tutoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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

import com.fasterxml.jackson.databind.JsonNode;

import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.support.IntegracaoTestBase;

// Chat entre tutor e estudante (Conversa e Mensagem), pela API
class ChatTest extends IntegracaoTestBase {

    @Autowired private AgendamentoRepository agendamentoRepository;

    private ResultActions iniciar(Usuario logado, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post("/conversa")
                .header("Authorization", tokenDe(logado))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corpo)));
    }

    private long conversaEntre(Tutor tutor, Estudante estudante) throws Exception {
        String corpo = iniciar(estudante.getUsuario(), Map.of("tutorId", tutor.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private ResultActions enviar(Usuario logado, long conversaId, String texto) throws Exception {
        return mockMvc.perform(post("/conversa/" + conversaId + "/mensagens")
                .header("Authorization", tokenDe(logado))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("conteudo", texto))));
    }

    private ResultActions mensagens(Usuario logado, long conversaId) throws Exception {
        return mockMvc.perform(get("/conversa/" + conversaId + "/mensagens")
                .header("Authorization", tokenDe(logado)));
    }

    private ResultActions listar(Usuario logado) throws Exception {
        return mockMvc.perform(get("/conversa").header("Authorization", tokenDe(logado)));
    }

    @Test
    void estudanteConversaComTutorEOTutorResponde() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante estudante = criarEstudante("aluno@teste.com");
        long conversa = conversaEntre(tutor, estudante);

        enviar(estudante.getUsuario(), conversa, "  Posso levar dúvidas de JOIN?  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conteudo").value("Posso levar dúvidas de JOIN?"))
                .andExpect(jsonPath("$.minha").value(true));

        // O tutor ve a conversa com 1 nao lida, o nome do estudante e a previa
        listar(tutor.getUsuario())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].interlocutorNome").value(estudante.getUsuario().getNome()))
                .andExpect(jsonPath("$[0].interlocutorPapel").value("ESTUDANTE"))
                .andExpect(jsonPath("$[0].ultimaMensagem").value("Posso levar dúvidas de JOIN?"))
                .andExpect(jsonPath("$[0].naoLidas").value(1))
                .andExpect(jsonPath("$[0].aberta").value(true));
        mockMvc.perform(get("/conversa/nao-lidas").header("Authorization", tokenDe(tutor.getUsuario())))
                .andExpect(jsonPath("$.total").value(1));

        // Abrir a conversa marca como lida
        mensagens(tutor.getUsuario(), conversa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].minha").value(false));
        listar(tutor.getUsuario()).andExpect(jsonPath("$[0].naoLidas").value(0));

        String resposta = enviar(tutor.getUsuario(), conversa, "Pode sim!")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long idResposta = objectMapper.readTree(resposta).get("id").asLong();

        // O estudante ve as duas mensagens; com aposId, so as mais novas
        mensagens(estudante.getUsuario(), conversa)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].conteudo").value("Pode sim!"))
                .andExpect(jsonPath("$[0].lidaEm").isNotEmpty());
        mockMvc.perform(get("/conversa/" + conversa + "/mensagens").param("aposId", String.valueOf(idResposta))
                .header("Authorization", tokenDe(estudante.getUsuario())))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void umaConversaPorDupla() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante estudante = criarEstudante("aluno@teste.com");

        long primeira = conversaEntre(tutor, estudante);
        long segunda = conversaEntre(tutor, estudante);

        assertThat(segunda).isEqualTo(primeira);
    }

    @Test
    void quemNaoEDaDuplaNaoLeNemEscreve() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante estudante = criarEstudante("aluno@teste.com");
        Estudante outro = criarEstudante("outro@teste.com");
        long conversa = conversaEntre(tutor, estudante);
        enviar(estudante.getUsuario(), conversa, "Mensagem particular").andExpect(status().isCreated());

        mensagens(outro.getUsuario(), conversa).andExpect(status().isForbidden());
        enviar(outro.getUsuario(), conversa, "oi").andExpect(status().isForbidden());

        // Nem a coordenacao le o chat dos outros
        mockMvc.perform(get("/conversa/" + conversa + "/mensagens").header("Authorization", tokenAdmin()))
                .andExpect(status().isForbidden());
        listar(outro.getUsuario()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void tutorSoIniciaConversaComQuemJaSolicitouTutoria() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante estudante = criarEstudante("aluno@teste.com");

        iniciar(tutor.getUsuario(), Map.of("estudanteId", estudante.getId()))
                .andExpect(status().isForbidden());

        Agendamento sessao = Agendamento.disponibilizar(tutor, LocalDate.now().plusDays(2), LocalTime.of(14, 0), null);
        sessao.agendar(estudante);
        agendamentoRepository.save(sessao);

        iniciar(tutor.getUsuario(), Map.of("estudanteId", estudante.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interlocutorPapel").value("ESTUDANTE"));
    }

    @Test
    void mensagemVaziaOuLongaDemaisERecusada() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante estudante = criarEstudante("aluno@teste.com");
        long conversa = conversaEntre(tutor, estudante);

        enviar(estudante.getUsuario(), conversa, "   ").andExpect(status().isBadRequest());
        enviar(estudante.getUsuario(), conversa, "a".repeat(2001)).andExpect(status().isBadRequest());
    }

    @Test
    void contaInativadaDeixaAConversaSomenteLeitura() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");
        Estudante estudante = criarEstudante("aluno@teste.com");
        long conversa = conversaEntre(tutor, estudante);
        enviar(estudante.getUsuario(), conversa, "Obrigado pela aula!").andExpect(status().isCreated());

        Usuario usuarioTutor = tutor.getUsuario();
        usuarioTutor.setStatus(UsuarioStatusEnum.INATIVO);
        usuarioRepository.save(usuarioTutor);

        listar(estudante.getUsuario()).andExpect(jsonPath("$[0].aberta").value(false));
        mensagens(estudante.getUsuario(), conversa).andExpect(jsonPath("$", hasSize(1)));
        enviar(estudante.getUsuario(), conversa, "Oi?")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("encerrada")));
    }

    @Test
    void coordenacaoNaoAbreConversa() throws Exception {
        Tutor tutor = criarTutor("tutor@teste.com");

        mockMvc.perform(post("/conversa")
                .header("Authorization", tokenAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("tutorId", tutor.getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void conversaInexistenteResponde404() throws Exception {
        Estudante estudante = criarEstudante("aluno@teste.com");

        JsonNode erro = objectMapper.readTree(mensagens(estudante.getUsuario(), 999999)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString());
        assertThat(erro.get("message").asText()).contains("Conversa");
    }
}
