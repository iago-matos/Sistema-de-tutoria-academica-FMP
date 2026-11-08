package br.com.tutoria.model;

import java.time.LocalDateTime;

import br.com.tutoria.model.enums.TutorDisciplinaStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Uma disciplina na lista de um tutor. A disciplina do cadastro nasce ATIVA
// (a coordenacao aprova junto com a conta); as pedidas depois nascem PENDENTE.
@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "tutor_disciplina",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tutor_id", "disciplina_id"}))
public class TutorDisciplina {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    private Tutor tutor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "disciplina_id", nullable = false)
    private Disciplina disciplina;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TutorDisciplinaStatusEnum status;

    @Column(nullable = false)
    private LocalDateTime solicitadaEm;

    public TutorDisciplina(Tutor tutor, Disciplina disciplina, TutorDisciplinaStatusEnum status,
            LocalDateTime solicitadaEm) {
        this.tutor = tutor;
        this.disciplina = disciplina;
        this.status = status;
        this.solicitadaEm = solicitadaEm;
    }

    public boolean isAtiva() {
        return status == TutorDisciplinaStatusEnum.ATIVA;
    }
}
