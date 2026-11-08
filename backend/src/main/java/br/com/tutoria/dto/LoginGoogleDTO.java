package br.com.tutoria.dto;

import br.com.tutoria.model.enums.UsuarioTipoEnum;
import jakarta.validation.constraints.NotBlank;

// Corpo do POST /login/google.
// credential: o ID token que o botao do Google entrega ao frontend.
// Os demais campos so sao usados no primeiro acesso, para criar a conta.
public record LoginGoogleDTO(

        @NotBlank(message = "Token do Google ausente")
        String credential,

        UsuarioTipoEnum tipo,

        // Obrigatorio no primeiro acesso de um TUTOR: uma disciplina da lista
        // (GET /disciplina). O estudante so informa o tipo.
        Long disciplinaId) {
}
