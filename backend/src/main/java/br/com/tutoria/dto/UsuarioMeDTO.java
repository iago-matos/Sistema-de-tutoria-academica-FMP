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

    // IDs dos perfis do usuario logado (null quando ele nao tem o perfil).
    // Evita que o frontend precise baixar a lista de todos os estudantes e
    // tutores so para descobrir o proprio ID.
    private Long estudanteId;
    private Long tutorId;
}
