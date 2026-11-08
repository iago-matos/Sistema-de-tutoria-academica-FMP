package br.com.tutoria.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Uma mensagem do chat. So nasce por Conversa.enviar(...), que confere se o
// remetente faz parte da dupla. Nao ha edicao: o conteudo e imutavel.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido pelo JPA
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "mensagem", indexes = @Index(name = "ix_mensagem_conversa_data", columnList = "conversa_id, enviada_em"))
public class Mensagem {

    public static final int MAX_CONTEUDO = 2000;

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversa_id", nullable = false)
    @JsonIgnore // as mensagens sao sempre buscadas a partir da conversa
    private Conversa conversa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remetente_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "email", "profiles", "status"})
    private Usuario remetente;

    // nvarchar(2000) no SQL Server
    @Column(nullable = false, length = MAX_CONTEUDO)
    private String conteudo;

    @Column(nullable = false)
    private LocalDateTime enviadaEm;

    // null enquanto o destinatario nao abriu a conversa
    private LocalDateTime lidaEm;

    // Package-private: so a Conversa cria mensagens
    Mensagem(Conversa conversa, Usuario remetente, String texto, LocalDateTime agora) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("A mensagem não pode ser vazia");
        }
        String conteudo = texto.trim();
        if (conteudo.length() > MAX_CONTEUDO) {
            throw new IllegalArgumentException("A mensagem deve ter no máximo " + MAX_CONTEUDO + " caracteres");
        }
        this.conversa = conversa;
        this.remetente = remetente;
        this.conteudo = conteudo;
        this.enviadaEm = agora;
    }

    // Chamado quando o destinatario abre a conversa. Quem enviou nao "le" a
    // propria mensagem, e uma mensagem ja lida mantem a data da primeira leitura.
    public void marcarComoLida(Usuario leitor, LocalDateTime agora) {
        if (lidaEm != null || Conversa.mesmoUsuario(leitor, remetente)) {
            return;
        }
        if (!conversa.participa(leitor)) {
            throw new IllegalArgumentException("Usuário não participa desta conversa");
        }
        this.lidaEm = agora;
    }

    public boolean isLida() {
        return lidaEm != null;
    }
}
