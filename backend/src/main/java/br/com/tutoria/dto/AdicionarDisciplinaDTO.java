package br.com.tutoria.dto;

import jakarta.validation.constraints.NotNull;

// Corpo do POST /tutor/me/disciplinas: o tutor pede uma disciplina da lista
public record AdicionarDisciplinaDTO(

        @NotNull(message = "Escolha a disciplina")
        Long disciplinaId) {
}
