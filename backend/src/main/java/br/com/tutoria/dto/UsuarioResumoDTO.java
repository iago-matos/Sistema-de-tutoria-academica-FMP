package br.com.tutoria.dto;

import java.util.List;

import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.model.enums.UsuarioTipoEnum;

// "disciplinas": so na lista de cadastros pendentes, para a coordenacao ver
// em que disciplina o tutor quer atuar antes de aprovar. Nas demais, null.
public record UsuarioResumoDTO(
        Long id,
        String nome,
        String email,
        UsuarioStatusEnum status,
        UsuarioTipoEnum tipo,
        List<String> disciplinas) {

    public UsuarioResumoDTO(Long id, String nome, String email, UsuarioStatusEnum status, UsuarioTipoEnum tipo) {
        this(id, nome, email, status, tipo, null);
    }
}
