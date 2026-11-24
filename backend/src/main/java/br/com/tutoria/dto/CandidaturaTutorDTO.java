package br.com.tutoria.dto;

import java.time.LocalDateTime;

// Situacao da candidatura do estudante logado a tutor (guia "Candidatar-se")
public record CandidaturaTutorDTO(
        // NENHUMA, PENDENTE, RECUSADA ou APROVADA
        String situacao,
        // Disciplina do pedido (pendente ou recusado); null em NENHUMA e APROVADA
        DisciplinaDTO disciplina,
        LocalDateTime solicitadaEm) {

    public static CandidaturaTutorDTO nenhuma() {
        return new CandidaturaTutorDTO("NENHUMA", null, null);
    }

    public static CandidaturaTutorDTO aprovada() {
        return new CandidaturaTutorDTO("APROVADA", null, null);
    }
}
