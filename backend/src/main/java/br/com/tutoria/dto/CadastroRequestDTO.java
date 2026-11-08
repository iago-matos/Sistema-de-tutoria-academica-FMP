package br.com.tutoria.dto;

import br.com.tutoria.model.enums.UsuarioTipoEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Dados aceitos no cadastro publico. Usar um DTO, e nao a entidade Usuario,
// impede que o cliente envie campos como "profiles" ou "status".
public record CadastroRequestDTO(

        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres")
        String nome,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        // BCrypt considera no maximo 72 bytes da senha
        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, max = 72, message = "Senha deve ter entre 8 e 72 caracteres")
        String senha,

        @NotNull(message = "Tipo é obrigatório: ESTUDANTE ou TUTOR")
        UsuarioTipoEnum tipo,

        // Obrigatorio quando tipo = TUTOR: uma disciplina da lista (GET /disciplina).
        // O estudante so informa o tipo.
        Long disciplinaId) {
}
