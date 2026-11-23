package br.com.tutoria.dto;

// Um aviso do sino de notificacoes. Nao fica gravado: e calculado a partir
// do estado atual (solicitacoes pendentes, mensagens nao lidas...), entao
// some sozinho quando a pessoa resolve a pendencia.
public record NotificacaoDTO(
        String tipo,
        String titulo,
        String descricao,
        // Pagina de destino a partir da raiz do frontend (ex.: "tutor/mensagens.html")
        String link,
        // Quanto soma no contador do sino
        long quantidade) {
}
