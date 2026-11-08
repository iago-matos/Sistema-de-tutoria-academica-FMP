package br.com.tutoria.dto;

import java.time.LocalDateTime;

import br.com.tutoria.model.TutorDisciplina;

// Pedido de disciplina nova de um tutor, na guia "Aprovações" da coordenacao
public record PedidoDisciplinaDTO(
        Long id,
        Long tutorId,
        String tutorNome,
        DisciplinaDTO disciplina,
        LocalDateTime solicitadaEm) {

    public static PedidoDisciplinaDTO de(TutorDisciplina pedido) {
        return new PedidoDisciplinaDTO(pedido.getId(), pedido.getTutor().getId(),
                pedido.getTutor().getUsuario().getNome(), DisciplinaDTO.de(pedido.getDisciplina()),
                pedido.getSolicitadaEm());
    }
}
