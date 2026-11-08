package br.com.tutoria.dto;

import br.com.tutoria.model.Disciplina;

public record DisciplinaDTO(Long id, String nome) {

    public static DisciplinaDTO de(Disciplina disciplina) {
        return new DisciplinaDTO(disciplina.getId(), disciplina.getNome());
    }
}
