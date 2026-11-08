package br.com.tutoria.model.enums;

// Situacao de uma disciplina na lista de um tutor
public enum TutorDisciplinaStatusEnum {

    // Aprovada: aparece na busca dos estudantes
    ATIVA,

    // Pedida pelo tutor depois do cadastro; espera a coordenacao aprovar
    PENDENTE,

    // A coordenacao recusou; o tutor ve o motivo na lista e pode remover
    RECUSADA
}
