package br.com.tutoria.dto;

import java.util.List;

// Visao publica de um tutor: o que qualquer usuario logado pode ver.
// Nao inclui e-mail nem as avaliacoes individuais, apenas a media e o total.
// "disciplinas": so as ativas (aprovadas pela coordenacao).
public record TutorResumoDTO(
        Long id,
        List<DisciplinaDTO> disciplinas,
        UsuarioNomeDTO usuario,
        Double mediaAvaliacoes,
        Long totalAvaliacoes) {

    public record UsuarioNomeDTO(Long id, String nome) {
    }
}
