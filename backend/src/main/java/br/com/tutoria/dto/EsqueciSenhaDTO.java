package br.com.tutoria.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EsqueciSenhaDTO(

        @NotBlank(message = "Informe o e-mail")
        @Email(message = "E-mail inválido")
        String email) {
}
