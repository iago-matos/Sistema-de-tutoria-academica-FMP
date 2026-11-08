package br.com.tutoria.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import br.com.tutoria.model.enums.UsuarioStatusEnum;

// Testes de unidade das regras de Conversa e Mensagem: rodam sem Spring e sem banco
class ConversaTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 12, 1, 14, 0);

    private final Usuario usuarioTutor = usuario(1L, "Prof. Ana");
    private final Usuario usuarioAluno = usuario(2L, "Iago");
    private final Usuario intruso = usuario(3L, "Outro aluno");

    private final Tutor tutor = tutor(usuarioTutor);
    private final Estudante estudante = estudante(usuarioAluno);

    private static Usuario usuario(Long id, String nome) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome(nome);
        usuario.setStatus(UsuarioStatusEnum.ATIVO);
        return usuario;
    }

    private static Tutor tutor(Usuario usuario) {
        Tutor tutor = new Tutor();
        tutor.setUsuario(usuario);
        return tutor;
    }

    private static Estudante estudante(Usuario usuario) {
        Estudante estudante = new Estudante();
        estudante.setUsuario(usuario);
        return estudante;
    }

    private Conversa conversa() {
        return Conversa.iniciar(tutor, estudante, AGORA);
    }

    @Test
    void soTutorEEstudanteDaDuplaParticipam() {
        Conversa conversa = conversa();

        assertThat(conversa.participa(usuarioTutor)).isTrue();
        assertThat(conversa.participa(usuarioAluno)).isTrue();
        assertThat(conversa.participa(intruso)).isFalse();
        assertThat(conversa.participa(null)).isFalse();
    }

    @Test
    void usuariosSemIdNaoSaoConfundidos() {
        Conversa conversa = Conversa.iniciar(tutor(usuario(null, "A")), estudante(usuario(null, "B")), AGORA);

        assertThat(conversa.participa(usuario(null, "C"))).isFalse();
    }

    @Test
    void naoAbreConversaConsigoMesmo() {
        assertThatThrownBy(() -> Conversa.iniciar(tutor, estudante(usuarioTutor), AGORA))
                .hasMessageContaining("consigo mesmo");
        assertThatThrownBy(() -> Conversa.iniciar(null, estudante, AGORA)).hasMessageContaining("tutor");
        assertThatThrownBy(() -> Conversa.iniciar(tutor, null, AGORA)).hasMessageContaining("estudante");
    }

    @Test
    void interlocutorEOOutroLado() {
        Conversa conversa = conversa();

        assertThat(conversa.interlocutorDe(usuarioAluno)).isSameAs(usuarioTutor);
        assertThat(conversa.interlocutorDe(usuarioTutor)).isSameAs(usuarioAluno);
        assertThatThrownBy(() -> conversa.interlocutorDe(intruso)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enviarCriaMensagemEAtualizaUltimaAtividade() {
        Conversa conversa = conversa();

        Mensagem mensagem = conversa.enviar(usuarioAluno, "  Posso levar dúvidas de JOIN?  ", AGORA.plusMinutes(5));

        assertThat(mensagem.getConversa()).isSameAs(conversa);
        assertThat(mensagem.getRemetente()).isSameAs(usuarioAluno);
        assertThat(mensagem.getConteudo()).isEqualTo("Posso levar dúvidas de JOIN?");
        assertThat(mensagem.isLida()).isFalse();
        assertThat(conversa.getUltimaMensagemEm()).isEqualTo(AGORA.plusMinutes(5));
    }

    @Test
    void quemNaoEDaDuplaNaoEnvia() {
        Conversa conversa = conversa();

        assertThatThrownBy(() -> conversa.enviar(intruso, "oi", AGORA))
                .hasMessageContaining("Só o tutor e o estudante");
        assertThat(conversa.getUltimaMensagemEm()).isNull();
    }

    @Test
    void conteudoNaoPodeSerVazioNemLongoDemais() {
        Conversa conversa = conversa();

        assertThatThrownBy(() -> conversa.enviar(usuarioAluno, "   ", AGORA)).hasMessageContaining("vazia");
        assertThatThrownBy(() -> conversa.enviar(usuarioAluno, null, AGORA)).hasMessageContaining("vazia");
        assertThatThrownBy(() -> conversa.enviar(usuarioAluno, "a".repeat(Mensagem.MAX_CONTEUDO + 1), AGORA))
                .hasMessageContaining(String.valueOf(Mensagem.MAX_CONTEUDO));
        assertThat(conversa.enviar(usuarioAluno, "a".repeat(Mensagem.MAX_CONTEUDO), AGORA)).isNotNull();
    }

    @Test
    void contaInativaEncerraAConversaMasGuardaOHistorico() {
        Conversa conversa = conversa();
        Mensagem antiga = conversa.enviar(usuarioAluno, "Obrigado pela aula!", AGORA);

        usuarioTutor.setStatus(UsuarioStatusEnum.INATIVO);

        assertThat(conversa.isAberta()).isFalse();
        assertThatThrownBy(() -> conversa.enviar(usuarioAluno, "Oi?", AGORA.plusDays(1)))
                .hasMessageContaining("encerrada")
                .hasMessageContaining("Prof. Ana");
        assertThat(antiga.getConteudo()).isEqualTo("Obrigado pela aula!");
        assertThat(conversa.getUltimaMensagemEm()).isEqualTo(AGORA);

        usuarioTutor.setStatus(UsuarioStatusEnum.ATIVO); // reativado: volta a aceitar mensagens
        assertThat(conversa.enviar(usuarioAluno, "Voltou!", AGORA.plusDays(2))).isNotNull();
    }

    @Test
    void naoAbreConversaComQuemNaoEstaAtivo() {
        usuarioTutor.setStatus(UsuarioStatusEnum.INATIVO);

        assertThatThrownBy(this::conversa).hasMessageContaining("conta ativa");
    }

    @Test
    void soODestinatarioMarcaComoLida() {
        Mensagem mensagem = conversa().enviar(usuarioAluno, "oi", AGORA);

        mensagem.marcarComoLida(usuarioAluno, AGORA.plusMinutes(1)); // o proprio remetente: ignora
        assertThat(mensagem.isLida()).isFalse();

        assertThatThrownBy(() -> mensagem.marcarComoLida(intruso, AGORA)).isInstanceOf(IllegalArgumentException.class);

        mensagem.marcarComoLida(usuarioTutor, AGORA.plusMinutes(2));
        mensagem.marcarComoLida(usuarioTutor, AGORA.plusMinutes(9)); // mantem a primeira leitura
        assertThat(mensagem.getLidaEm()).isEqualTo(AGORA.plusMinutes(2));
    }
}
