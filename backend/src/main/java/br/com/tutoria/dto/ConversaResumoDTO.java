package br.com.tutoria.dto;

import java.time.LocalDateTime;

// Uma conversa na lista do chat, do ponto de vista de quem esta logado
public record ConversaResumoDTO(
        Long id,
        Long tutorId,
        Long estudanteId,
        // Quem esta do outro lado e o papel dele na dupla (TUTOR ou ESTUDANTE)
        String interlocutorNome,
        String interlocutorPapel,
        // Previa da ultima mensagem (null em conversa ainda vazia)
        String ultimaMensagem,
        boolean ultimaMensagemMinha,
        LocalDateTime ultimaMensagemEm,
        long naoLidas,
        // false quando tutor ou estudante foi inativado: so leitura
        boolean aberta) {
}
