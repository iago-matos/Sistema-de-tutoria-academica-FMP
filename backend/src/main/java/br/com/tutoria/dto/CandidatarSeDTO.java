package br.com.tutoria.dto;

import jakarta.validation.constraints.NotNull;

public record CandidatarSeDTO(

        @NotNull(message = "Escolha a disciplina em que quer ser tutor")
        Long disciplinaId) {
}
