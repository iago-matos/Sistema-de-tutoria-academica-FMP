package br.com.tutoria.dto;

// Resultado agregado das avaliacoes de um tutor, calculado pelo banco
public record AvaliacaoEstatisticaDTO(Long tutorId, Double media, Long total) {
}
