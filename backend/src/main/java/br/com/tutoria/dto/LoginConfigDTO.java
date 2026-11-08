package br.com.tutoria.dto;

import java.util.List;

// Opcoes da tela de login (GET /login/config, rota publica). Assim o frontend
// nao repete o client ID do Google, que fica so na configuracao do backend.
public record LoginConfigDTO(
        // Vazio quando o login com Google nao esta configurado
        String googleClientId,
        List<String> dominiosInstitucionais,
        boolean cadastroComSenha) {
}
