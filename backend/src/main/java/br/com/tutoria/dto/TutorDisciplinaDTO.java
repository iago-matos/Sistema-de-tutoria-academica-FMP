package br.com.tutoria.dto;

import java.time.LocalDateTime;

import br.com.tutoria.model.TutorDisciplina;
import br.com.tutoria.model.enums.TutorDisciplinaStatusEnum;

// Uma linha da guia "Disciplinas" do tutor
public record TutorDisciplinaDTO(
        Long id,
        DisciplinaDTO disciplina,
        TutorDisciplinaStatusEnum status,
        LocalDateTime solicitadaEm) {

    public static TutorDisciplinaDTO de(TutorDisciplina vinculo) {
        return new TutorDisciplinaDTO(vinculo.getId(), DisciplinaDTO.de(vinculo.getDisciplina()),
                vinculo.getStatus(), vinculo.getSolicitadaEm());
    }
}
