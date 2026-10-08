package br.com.tutoria.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// Edicao de dados cadastrais. Nao tem campo de senha de proposito: a senha so
// muda por PUT /usuario/me/senha, que exige a senha atual.
// Campos nulos nao sao alterados.
public record AtualizarUsuarioDTO(

        @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres")
        String nome,

        @Email(message = "Email inválido")
        String email) {
}
