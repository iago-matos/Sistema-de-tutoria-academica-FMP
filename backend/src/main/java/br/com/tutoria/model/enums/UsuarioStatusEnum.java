package br.com.tutoria.model.enums;

public enum UsuarioStatusEnum {
    // Cadastrou-se e aguarda um administrador
    PENDENTE,
    // Aprovado: pode fazer login
    ATIVO,
    // Cadastro recusado por um administrador
    RECUSADO
}
