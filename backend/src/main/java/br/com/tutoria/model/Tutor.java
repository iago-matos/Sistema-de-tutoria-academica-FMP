package br.com.tutoria.model;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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
@Table(name = "tutor")
public class Tutor {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Disciplinas do tutor (ativas, pendentes e recusadas). O antigo campo de
    // texto "disciplina" foi migrado para esta lista pelo AjusteDeEsquema.
    @OneToMany(mappedBy = "tutor")
    @JsonIgnore
    private List<TutorDisciplina> vinculosDisciplina = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    // Dentro de um perfil, o usuario aparece so com id e nome: e-mail, perfis
    // e status ficam restritos ao endpoint de usuarios (somente ADMIN)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "email", "profiles", "status"})
    private Usuario usuario;

    @OneToMany(mappedBy = "tutor")
    @JsonIgnore
    private List<Agendamento> agendamentos = new ArrayList<>();

    @OneToMany(mappedBy = "tutor")
    @JsonIgnore
    private List<Avaliacao> avaliacoes = new ArrayList<>();

    // Vai no JSON como "disciplinas": so as aprovadas, em ordem alfabetica
    @JsonProperty("disciplinas")
    public List<Disciplina> getDisciplinasAtivas() {
        return vinculosDisciplina.stream()
                .filter(TutorDisciplina::isAtiva)
                .map(TutorDisciplina::getDisciplina)
                .sorted(Comparator.comparing(Disciplina::getNome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
