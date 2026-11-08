package br.com.tutoria.model.enums;

public enum UsuarioStatusEnum {
    // Cadastrou-se e aguarda um administrador
    PENDENTE,
    // Aprovado: pode fazer login
    ATIVO,
    // Cadastro recusado por um administrador
    RECUSADO,
    // Conta desativada pela coordenacao: nao faz login, mas os dados ficam
    // guardados (historico de sessoes, avaliacoes e mensagens do chat)
    INATIVO,
    // Dados pessoais apagados a pedido do proprio usuario, feito na secretaria.
    // Definitivo: nome e e-mail foram substituidos e a conta nao pode ser reativada.
    // O historico continua, mas sem dizer de quem e (LGPD).
    ANONIMIZADO
}
