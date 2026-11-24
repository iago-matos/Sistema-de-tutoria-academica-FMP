package br.com.tutoria.dto;

import java.time.LocalDateTime;

import br.com.tutoria.model.Mensagem;

// Mensagem do chat como a tela precisa: "minha" decide o lado do balao
public record MensagemDTO(
        Long id,
        String remetenteNome,
        String conteudo,
        LocalDateTime enviadaEm,
        LocalDateTime lidaEm,
        boolean minha) {

    public static MensagemDTO de(Mensagem mensagem, Long usuarioLogadoId) {
        return new MensagemDTO(
                mensagem.getId(),
                mensagem.getRemetente().getNome(),
                mensagem.getConteudo(),
                mensagem.getEnviadaEm(),
                mensagem.getLidaEm(),
                mensagem.getRemetente().getId().equals(usuarioLogadoId));
    }
}
