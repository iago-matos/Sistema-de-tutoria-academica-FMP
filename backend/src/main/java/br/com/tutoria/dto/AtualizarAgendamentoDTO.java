package br.com.tutoria.dto;

import java.time.LocalDate;
import java.time.LocalTime;

// Corpo do PUT /agendamento/{id} (tutor da sessao ou admin). Todos os campos
// sao opcionais. status aceita CONFIRMADO, CANCELADO ou REALIZADO.
// linkReuniao: null nao muda nada; texto vazio remove o link.
public record AtualizarAgendamentoDTO(
        String status,
        LocalDate data,
        LocalTime hora,
        String observacoes,
        String linkReuniao) {
}
