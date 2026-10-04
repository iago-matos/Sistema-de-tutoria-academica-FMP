package br.com.tutoria.dto;

import br.com.tutoria.model.enums.UsuarioTipoEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioMeDTO {

    private Long id;
    private String nome;
    private String email;
    private UsuarioTipoEnum tipo;
}
