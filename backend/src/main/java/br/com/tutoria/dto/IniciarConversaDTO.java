package br.com.tutoria.dto;

// O estudante informa o tutor; o tutor informa o estudante.
// Quem esta logado e sempre o outro lado da dupla (vem do token).
public record IniciarConversaDTO(Long tutorId, Long estudanteId) {
}
