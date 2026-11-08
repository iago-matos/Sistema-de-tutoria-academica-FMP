package br.com.tutoria.dto;

import br.com.tutoria.model.Disciplina;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Corpo do POST e do PUT /disciplina (coordenacao cria ou renomeia)
public record NomeDisciplinaDTO(

        @NotBlank(message = "Informe o nome da disciplina")
        @Size(max = Disciplina.MAX_NOME, message = "O nome deve ter no máximo 120 caracteres")
        String nome) {
}
