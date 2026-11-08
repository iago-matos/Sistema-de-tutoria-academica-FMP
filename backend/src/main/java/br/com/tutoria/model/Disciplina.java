package br.com.tutoria.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Disciplina da lista mantida pela coordenacao (admin). O tutor escolhe dessa
// lista, em vez de digitar: assim "Banco de Dados" e "banco de dados" nao viram
// duas disciplinas diferentes na busca do estudante.
@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "disciplina")
public class Disciplina {

    public static final int MAX_NOME = 120;

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = MAX_NOME)
    private String nome;

    public Disciplina(String nome) {
        this.nome = nome;
    }
}
