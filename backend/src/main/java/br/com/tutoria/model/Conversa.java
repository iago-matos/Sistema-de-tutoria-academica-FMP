package br.com.tutoria.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.tutoria.model.enums.UsuarioStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Chat entre UM tutor e UM estudante. Existe no maximo uma conversa por dupla
// (restricao unica no banco): se o aluno fala com o tutor de Banco de Dados e com
// o de Programacao, sao duas conversas separadas, cada uma com todo o seu historico,
// independente de quantas sessoes eles marcarem.
//
// As mensagens apontam para a conversa (Mensagem -> Conversa), e nao o contrario:
// assim abrir a lista de conversas nao carrega o historico inteiro de cada uma.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido pelo JPA
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "conversa", uniqueConstraints = @UniqueConstraint(
        name = "uk_conversa_tutor_estudante", columnNames = {"tutor_id", "estudante_id"}))
public class Conversa {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "agendamentos", "avaliacoes"})
    private Tutor tutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudante_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "agendamentos", "avaliacoes"})
    private Estudante estudante;

    @Column(nullable = false)
    private LocalDateTime criadaEm;

    // Usado para ordenar a lista de conversas (mais recente primeiro)
    private LocalDateTime ultimaMensagemEm;

    // ── Criacao ──────────────────────────────────────────────────────────────

    public static Conversa iniciar(Tutor tutor, Estudante estudante, LocalDateTime agora) {
        if (tutor == null) {
            throw new IllegalArgumentException("O tutor é obrigatório");
        }
        if (estudante == null) {
            throw new IllegalArgumentException("O estudante é obrigatório");
        }
        if (mesmoUsuario(tutor.getUsuario(), estudante.getUsuario())) {
            throw new IllegalArgumentException("Não é possível abrir uma conversa consigo mesmo");
        }
        Conversa conversa = new Conversa();
        conversa.tutor = tutor;
        conversa.estudante = estudante;
        conversa.criadaEm = agora;
        if (!conversa.isAberta()) {
            throw new IllegalArgumentException("Só é possível abrir conversa entre tutor e estudante com a conta ativa");
        }
        return conversa;
    }

    // ── Regras ───────────────────────────────────────────────────────────────

    // Cria a mensagem; quem salva no banco e o service.
    // So o tutor e o estudante da dupla podem escrever (nem admin escreve no chat dos outros).
    // Se um dos dois foi inativado, a conversa vira somente leitura: o historico
    // continua visivel, mas ninguem escreve para quem nao pode mais responder.
    public Mensagem enviar(Usuario remetente, String texto, LocalDateTime agora) {
        if (!participa(remetente)) {
            throw new IllegalArgumentException("Só o tutor e o estudante desta conversa podem enviar mensagens");
        }
        if (!isAberta()) {
            throw new IllegalArgumentException(
                    "Esta conversa está encerrada: " + interlocutorDe(remetente).getNome() + " não está mais ativo(a)");
        }
        Mensagem mensagem = new Mensagem(this, remetente, texto, agora);
        this.ultimaMensagemEm = agora;
        return mensagem;
    }

    // ── Consultas (sem efeito colateral) ─────────────────────────────────────

    // Aceita mensagens novas so enquanto tutor e estudante estao com a conta ATIVA.
    // A tela usa isso para trocar a caixa de texto por "conversa encerrada".
    public boolean isAberta() {
        return tutor.getUsuario().getStatus() == UsuarioStatusEnum.ATIVO
                && estudante.getUsuario().getStatus() == UsuarioStatusEnum.ATIVO;
    }

    public boolean participa(Usuario usuario) {
        return mesmoUsuario(usuario, tutor.getUsuario()) || mesmoUsuario(usuario, estudante.getUsuario());
    }

    // O "outro lado" da conversa, para a tela mostrar com quem o usuario esta falando
    public Usuario interlocutorDe(Usuario usuario) {
        if (mesmoUsuario(usuario, tutor.getUsuario())) {
            return estudante.getUsuario();
        }
        if (mesmoUsuario(usuario, estudante.getUsuario())) {
            return tutor.getUsuario();
        }
        throw new IllegalArgumentException("Usuário não participa desta conversa");
    }

    // Compara por id. Nao usa Usuario.equals porque ele considera iguais
    // dois usuarios ainda sem id (ambos null), o que liberaria o chat por engano.
    static boolean mesmoUsuario(Usuario a, Usuario b) {
        if (a == null || b == null) {
            return false;
        }
        return a == b || (a.getId() != null && a.getId().equals(b.getId()));
    }
}
