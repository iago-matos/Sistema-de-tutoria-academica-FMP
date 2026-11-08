package br.com.tutoria.dto;

// Corpo (opcional) do PUT /agendamento/{id}/solicitar.
// modalidade: PRESENCIAL ou ONLINE. Sem modalidade, a tutoria e presencial.
// disciplinaId: a disciplina da tutoria, entre as ativas do tutor. Pode faltar
// quando o tutor atende uma disciplina so.
public record SolicitarAgendamentoDTO(String modalidade, Long disciplinaId) {
}
