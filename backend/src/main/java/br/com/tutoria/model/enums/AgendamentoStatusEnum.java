package br.com.tutoria.model.enums;

// Ciclo de vida de um agendamento:
//
//   DISPONIVEL --agendar--> PENDENTE --confirmar--> CONFIRMADO --concluir--> REALIZADO
//        ^                     |                        |
//        +------desistir-------+------------------------+   (estudante libera a vaga, ate a vespera)
//
//   DISPONIVEL, PENDENTE ou CONFIRMADO --cancelar--> CANCELADO   (tutor ou admin)
public enum AgendamentoStatusEnum {
    // Tutor abriu o horario e aguarda um estudante
    DISPONIVEL,
    // Estudante solicitou e aguarda a confirmacao do tutor
    PENDENTE,
    // Tutor aceitou a solicitacao
    CONFIRMADO,
    // Sessao concluida
    REALIZADO,
    // Cancelada pelo tutor ou por um administrador
    CANCELADO
}
