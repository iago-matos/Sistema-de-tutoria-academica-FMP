package br.com.tutoria.dto;

import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.model.enums.UsuarioTipoEnum;

public record UsuarioResumoDTO(
        Long id,
        String nome,
        String email,
        UsuarioStatusEnum status,
        UsuarioTipoEnum tipo) {
}
