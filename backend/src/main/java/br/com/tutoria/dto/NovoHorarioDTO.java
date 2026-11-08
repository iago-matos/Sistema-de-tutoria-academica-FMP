package br.com.tutoria.dto;

import java.time.LocalDate;
import java.time.LocalTime;

// Corpo do POST /agendamento. O status e o estudante nao vem do cliente:
// o horario sempre nasce DISPONIVEL. tutorId so e considerado para administradores
// (um tutor sempre cria na propria agenda). linkReuniao e opcional: com ele,
// o estudante tambem pode escolher a tutoria online.
public record NovoHorarioDTO(
        LocalDate data,
        LocalTime hora,
        String observacoes,
        String linkReuniao,
        Long tutorId) {
}
