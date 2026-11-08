package br.com.tutoria.dto;

// Resposta do POST /login/google. Em AUTENTICADO, o token JWT vai no header
// Authorization, como no POST /login.
public record LoginGoogleRespostaDTO(
        Situacao situacao,
        String nome,
        String email,
        String message) {

    public enum Situacao {
        // Login feito: o token esta no header Authorization
        AUTENTICADO,
        // Primeiro acesso: o frontend pergunta o perfil e envia de novo
        CADASTRO_NECESSARIO,
        // Conta de tutor criada; espera a aprovacao de um administrador
        AGUARDANDO_APROVACAO
    }
}
