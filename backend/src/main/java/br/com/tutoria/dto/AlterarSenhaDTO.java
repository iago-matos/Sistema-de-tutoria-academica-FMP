package br.com.tutoria.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlterarSenhaDTO(

        @NotBlank(message = "Informe a senha atual")
        String senhaAtual,

        // BCrypt considera no maximo 72 bytes da senha
        @NotBlank(message = "Informe a nova senha")
        @Size(min = 8, max = 72, message = "A nova senha deve ter entre 8 e 72 caracteres")
        String novaSenha) {
}
