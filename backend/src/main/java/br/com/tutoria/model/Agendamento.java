package br.com.tutoria.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.tutoria.model.enums.AgendamentoStatusEnum;
import br.com.tutoria.model.enums.ModalidadeEnum;
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
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Coracao do sistema: um horario de tutoria de 1 hora oferecido por um tutor.
//
// As regras de negocio moram aqui, nos metodos da propria classe (agendar,
// confirmar, cancelar...). Nao ha setters: o status so muda por esses metodos,
// entao e impossivel, por exemplo, "confirmar" um horario que ninguem solicitou.
//
// Regras que dependem do banco ou do relogio (conflito de horario, horario no
// passado, quem esta logado) ficam no AgendamentoService.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido pelo JPA
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "agendamento")
public class Agendamento {

    // Por enquanto toda sessao dura 1 hora. Duracao configuravel e uma feature futura.
    public static final int DURACAO_MINUTOS = 60;

    // Ultimo inicio possivel para a sessao terminar no mesmo dia
    private static final LocalTime ULTIMO_INICIO = LocalTime.MIDNIGHT.minusMinutes(DURACAO_MINUTOS);

    private static final int MAX_OBSERVACOES = 4000;

    private static final int MAX_LINK = 500;

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Gravado como texto (DISPONIVEL, PENDENTE...): compativel com os dados antigos
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgendamentoStatusEnum status;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private LocalTime hora;

    // Texto livre de ate 4000 caracteres (nvarchar(4000) no SQL Server)
    @Column(length = MAX_OBSERVACOES)
    private String observacoes;

    // Link de videoconferencia (Meet, Zoom, Teams...) colado pelo tutor.
    // Opcional: sem link, o horario e so presencial. Vai no JSON para que o
    // estudante logado veja o link no popup da disciplina antes de solicitar
    // (decisao de produto). As rotas de horarios exigem login.
    @Column(length = MAX_LINK)
    private String linkReuniao;

    // Escolhida pelo estudante ao agendar; nula enquanto o horario esta livre
    @Enumerated(EnumType.STRING)
    private ModalidadeEnum modalidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "agendamentos", "avaliacoes"})
    private Tutor tutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudante_id", nullable = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "agendamentos", "avaliacoes"})
    private Estudante estudante;

    // Disciplina escolhida pelo estudante ao solicitar. O horario livre nao tem
    // disciplina: ele aparece em todas as disciplinas ativas do tutor.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disciplina_id", nullable = true)
    private Disciplina disciplina;

    // ── Criacao ──────────────────────────────────────────────────────────────

    // O tutor abre um horario na agenda; ele nasce DISPONIVEL e sem estudante
    public static Agendamento disponibilizar(Tutor tutor, LocalDate data, LocalTime hora, String observacoes) {
        return disponibilizar(tutor, data, hora, observacoes, null);
    }

    // Com link de videoconferencia, o estudante tambem pode escolher a opcao online
    public static Agendamento disponibilizar(
            Tutor tutor, LocalDate data, LocalTime hora, String observacoes, String linkReuniao) {
        if (tutor == null) {
            throw new IllegalArgumentException("O tutor é obrigatório");
        }
        Agendamento agendamento = new Agendamento();
        agendamento.tutor = tutor;
        agendamento.definirHorario(data, hora);
        agendamento.alterarObservacoes(observacoes);
        agendamento.linkReuniao = validarLink(linkReuniao);
        agendamento.status = AgendamentoStatusEnum.DISPONIVEL;
        return agendamento;
    }

    // ── Metodos do diagrama de classes ───────────────────────────────────────

    // Estudante reserva o horario. Retorna false se ele nao estiver mais livre.
    // (No diagrama: agendar(estudante, tutor). O tutor ja faz parte do horario,
    // pois foi ele quem o disponibilizou, entao basta informar o estudante.)
    // Sem modalidade informada, a tutoria e presencial (na instituicao)
    public boolean agendar(Estudante estudante) {
        return agendar(estudante, ModalidadeEnum.PRESENCIAL);
    }

    public boolean agendar(Estudante estudante, ModalidadeEnum modalidade) {
        return agendar(estudante, modalidade, null);
    }

    // A disciplina vem do AgendamentoService, que confere se o tutor a atende
    public boolean agendar(Estudante estudante, ModalidadeEnum modalidade, Disciplina disciplina) {
        if (estudante == null) {
            throw new IllegalArgumentException("O estudante é obrigatório");
        }
        if (modalidade == null) {
            throw new IllegalArgumentException("Escolha a modalidade: PRESENCIAL ou ONLINE");
        }
        if (!isDisponivel()) {
            return false;
        }
        if (modalidade == ModalidadeEnum.ONLINE && !isOnlineDisponivel()) {
            throw new IllegalArgumentException("Este horário não tem opção online, apenas presencial");
        }
        this.estudante = estudante;
        this.modalidade = modalidade;
        this.disciplina = disciplina;
        this.status = AgendamentoStatusEnum.PENDENTE;
        return true;
    }

    // Tutor (ou admin) cancela a sessao. Retorna o novo status.
    public AgendamentoStatusEnum cancelar() {
        if (status == AgendamentoStatusEnum.REALIZADO || status == AgendamentoStatusEnum.CANCELADO) {
            throw new IllegalArgumentException("Não é possível cancelar uma sessão " + descreverStatus());
        }
        this.status = AgendamentoStatusEnum.CANCELADO;
        return status;
    }

    // ── Demais transicoes ────────────────────────────────────────────────────

    // Estudante desiste: a vaga volta a ficar livre para outros estudantes
    public AgendamentoStatusEnum desistir() {
        if (status != AgendamentoStatusEnum.PENDENTE && status != AgendamentoStatusEnum.CONFIRMADO) {
            throw new IllegalArgumentException("Só é possível desistir de sessões pendentes ou confirmadas");
        }
        this.estudante = null;
        this.modalidade = null;
        this.disciplina = null;
        this.status = AgendamentoStatusEnum.DISPONIVEL;
        return status;
    }

    public void confirmar() {
        if (status != AgendamentoStatusEnum.PENDENTE) {
            throw new IllegalArgumentException("Só é possível confirmar solicitações pendentes");
        }
        this.status = AgendamentoStatusEnum.CONFIRMADO;
    }

    public void concluir() {
        if (status != AgendamentoStatusEnum.CONFIRMADO) {
            throw new IllegalArgumentException("Só é possível concluir sessões confirmadas");
        }
        this.status = AgendamentoStatusEnum.REALIZADO;
    }

    // Mudar data/hora so enquanto ninguem reservou: depois disso, o combinado
    // com o estudante nao pode mudar sem ele saber
    public void remarcar(LocalDate novaData, LocalTime novaHora) {
        if (!isDisponivel()) {
            throw new IllegalArgumentException(
                    "Só é possível remarcar horários que ainda não foram solicitados. Cancele e crie outro horário.");
        }
        definirHorario(novaData, novaHora);
    }

    public void alterarObservacoes(String observacoes) {
        if (observacoes != null && observacoes.length() > MAX_OBSERVACOES) {
            throw new IllegalArgumentException("Observações devem ter no máximo " + MAX_OBSERVACOES + " caracteres");
        }
        this.observacoes = observacoes == null || observacoes.isBlank() ? null : observacoes.trim();
    }

    // Colar, trocar ou remover (vazio) o link. Remover nao e permitido se o
    // estudante ja escolheu fazer a tutoria online.
    public void alterarLinkReuniao(String novoLink) {
        String link = validarLink(novoLink);
        boolean reservadaOnline = modalidade == ModalidadeEnum.ONLINE
                && (status == AgendamentoStatusEnum.PENDENTE || status == AgendamentoStatusEnum.CONFIRMADO);
        if (link == null && reservadaOnline) {
            throw new IllegalArgumentException(
                    "O estudante escolheu a tutoria online: troque o link, mas não o remova");
        }
        this.linkReuniao = link;
    }

    // ── Consultas (sem efeito colateral) ─────────────────────────────────────

    // Vai no JSON como "onlineDisponivel": diz se ha opcao online
    public boolean isOnlineDisponivel() {
        return linkReuniao != null;
    }

    // O estudante so recebe o link depois que o tutor confirma a sessao online
    public boolean linkLiberadoParaEstudante() {
        return modalidade == ModalidadeEnum.ONLINE && status == AgendamentoStatusEnum.CONFIRMADO;
    }

    public boolean isDisponivel() {
        return status == AgendamentoStatusEnum.DISPONIVEL && estudante == null;
    }

    // Horario de termino, enviado no JSON para as telas mostrarem "14:00 – 15:00"
    public LocalTime getHoraFim() {
        return hora == null ? null : hora.plusMinutes(DURACAO_MINUTOS);
    }

    public int getDuracaoMinutos() {
        return DURACAO_MINUTOS;
    }

    // Sessoes canceladas liberam o horario na agenda do tutor e do estudante
    public boolean ocupaAgenda() {
        return status != AgendamentoStatusEnum.CANCELADO;
    }

    public boolean comecouAte(LocalDateTime instante) {
        return !LocalDateTime.of(data, hora).isAfter(instante);
    }

    // Duas sessoes de 1h se sobrepoem se uma comeca antes da outra terminar
    public boolean sobrepoe(Agendamento outra) {
        return data.equals(outra.data)
                && hora.isBefore(outra.getHoraFim())
                && outra.hora.isBefore(getHoraFim());
    }

    private void definirHorario(LocalDate data, LocalTime hora) {
        if (data == null) {
            throw new IllegalArgumentException("O campo 'data' é obrigatório");
        }
        if (hora == null) {
            throw new IllegalArgumentException("O campo 'hora' é obrigatório");
        }
        LocalTime inicio = hora.withSecond(0).withNano(0);
        if (inicio.isAfter(ULTIMO_INICIO)) {
            throw new IllegalArgumentException("A sessão dura 1 hora e precisa começar até " + ULTIMO_INICIO);
        }
        this.data = data;
        this.hora = inicio;
    }

    // Aceita qualquer videoconferencia, desde que seja um endereco https://.
    // Isso tambem impede links "javascript:" que executariam codigo na tela.
    private static String validarLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String limpo = link.trim();
        String erro = "Informe um link válido começando com https:// (ex.: https://meet.google.com/abc-defg-hij)";
        if (limpo.length() > MAX_LINK) {
            throw new IllegalArgumentException("O link deve ter no máximo " + MAX_LINK + " caracteres");
        }
        try {
            URI uri = new URI(limpo);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException(erro);
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(erro);
        }
        return limpo;
    }

    private String descreverStatus() {
        return status == AgendamentoStatusEnum.REALIZADO ? "já realizada" : "já cancelada";
    }
}
