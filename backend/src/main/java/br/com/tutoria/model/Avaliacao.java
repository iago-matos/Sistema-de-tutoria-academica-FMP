package br.com.tutoria.model;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "avaliacao")
public class Avaliacao {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate dataAvaliacao;

    @Column(nullable = false)
    private LocalTime horaAvaliacao;

    @Column(nullable = false)
    private Integer nota;

    // Observacao opcional do estudante (nvarchar(500) no SQL Server)
    public static final int MAX_COMENTARIO = 500;

    @Column(length = MAX_COMENTARIO)
    private String comentario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "agendamentos", "avaliacoes"})
    private Tutor tutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudante_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "agendamentos", "avaliacoes"})
    private Estudante estudante;

    // A tutoria avaliada: cada tutoria recebe no maximo uma avaliacao
    // (conferido no AvaliacaoService). Pode ser nula nas avaliacoes antigas,
    // feitas antes desta regra. Por isso nao ha UNIQUE no banco: no SQL Server
    // uma coluna UNIQUE aceita um unico NULL.
    // No JSON: recebe {"agendamento": {"id": 1}} e devolve "agendamentoId".
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Agendamento agendamento;

    @JsonProperty(value = "agendamentoId", access = JsonProperty.Access.READ_ONLY)
    public Long getAgendamentoId() {
        return agendamento == null ? null : agendamento.getId();
    }

    // Disciplina da tutoria avaliada (nula nas avaliacoes antigas)
    @JsonProperty(value = "disciplina", access = JsonProperty.Access.READ_ONLY)
    public Disciplina getDisciplinaDaTutoria() {
        return agendamento == null ? null : agendamento.getDisciplina();
    }

}
