package br.com.tutoria.dto;

// Visao publica de um tutor: o que qualquer usuario logado pode ver.
// Nao inclui e-mail nem as avaliacoes individuais, apenas a media e o total.
public record TutorResumoDTO(
        Long id,
        String disciplina,
        UsuarioNomeDTO usuario,
        Double mediaAvaliacoes,
        Long totalAvaliacoes) {

    public record UsuarioNomeDTO(Long id, String nome) {
    }
}
