package br.com.tutoria.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import br.com.tutoria.model.enums.AgendamentoStatusEnum;

// Testes de unidade das regras da classe Agendamento: rodam sem Spring e sem banco
class AgendamentoTest {

    private static final LocalDate DIA = LocalDate.of(2026, 12, 1);

    private final Tutor tutor = new Tutor();
    private final Estudante ana = new Estudante();
    private final Estudante bia = new Estudante();

    private Agendamento horario(int hora) {
        return Agendamento.disponibilizar(tutor, DIA, LocalTime.of(hora, 0), "Trazer a lista 3");
    }

    @Test
    void nasceDisponivelComDuracaoDeUmaHora() {
        Agendamento agendamento = horario(14);

        assertThat(agendamento.getStatus()).isEqualTo(AgendamentoStatusEnum.DISPONIVEL);
        assertThat(agendamento.getEstudante()).isNull();
        assertThat(agendamento.getDuracaoMinutos()).isEqualTo(60);
        assertThat(agendamento.getHoraFim()).isEqualTo(LocalTime.of(15, 0));
    }

    @Test
    void exigeDataEHoraEUmHorarioQueTermineNoMesmoDia() {
        assertThatThrownBy(() -> Agendamento.disponibilizar(tutor, null, LocalTime.NOON, null))
                .hasMessageContaining("data");
        assertThatThrownBy(() -> Agendamento.disponibilizar(tutor, DIA, null, null))
                .hasMessageContaining("hora");
        assertThatThrownBy(() -> Agendamento.disponibilizar(tutor, DIA, LocalTime.of(23, 30), null))
                .hasMessageContaining("1 hora");
    }

    @Test
    void agendarSoFuncionaUmaVez() {
        Agendamento agendamento = horario(14);

        assertThat(agendamento.agendar(ana)).isTrue();
        assertThat(agendamento.getStatus()).isEqualTo(AgendamentoStatusEnum.PENDENTE);
        assertThat(agendamento.getEstudante()).isSameAs(ana);

        assertThat(agendamento.agendar(bia)).isFalse();
        assertThat(agendamento.getEstudante()).isSameAs(ana);
    }

    @Test
    void cicloCompletoAteRealizado() {
        Agendamento agendamento = horario(14);
        agendamento.agendar(ana);
        agendamento.confirmar();
        agendamento.concluir();

        assertThat(agendamento.getStatus()).isEqualTo(AgendamentoStatusEnum.REALIZADO);
        assertThatThrownBy(agendamento::cancelar).hasMessageContaining("já realizada");
    }

    @Test
    void naoConfirmaNemConcluiForaDeOrdem() {
        Agendamento agendamento = horario(14);

        assertThatThrownBy(agendamento::confirmar).hasMessageContaining("pendentes");
        assertThatThrownBy(agendamento::concluir).hasMessageContaining("confirmadas");
    }

    @Test
    void cancelarRetornaONovoStatusENaoPodeRepetir() {
        Agendamento agendamento = horario(14);

        assertThat(agendamento.cancelar()).isEqualTo(AgendamentoStatusEnum.CANCELADO);
        assertThat(agendamento.ocupaAgenda()).isFalse();
        assertThatThrownBy(agendamento::cancelar).hasMessageContaining("já cancelada");
    }

    @Test
    void estudanteDesisteEOHorarioVoltaASerDisponivel() {
        Agendamento agendamento = horario(14);
        agendamento.agendar(ana);
        agendamento.confirmar();

        assertThat(agendamento.desistir()).isEqualTo(AgendamentoStatusEnum.DISPONIVEL);
        assertThat(agendamento.getEstudante()).isNull();
        assertThat(agendamento.agendar(bia)).isTrue();
    }

    @Test
    void soRemarcaEnquantoNinguemReservou() {
        Agendamento agendamento = horario(14);
        agendamento.remarcar(DIA.plusDays(1), LocalTime.of(10, 0));
        assertThat(agendamento.getData()).isEqualTo(DIA.plusDays(1));

        agendamento.agendar(ana);
        assertThatThrownBy(() -> agendamento.remarcar(DIA, LocalTime.of(9, 0)))
                .hasMessageContaining("remarcar");
    }

    @Test
    void sessoesDeUmaHoraSeSobrepoemSoSeOsIntervalosSeCruzam() {
        Agendamento dasDuas = horario(14);

        assertThat(dasDuas.sobrepoe(horario(14))).isTrue();
        assertThat(dasDuas.sobrepoe(Agendamento.disponibilizar(tutor, DIA, LocalTime.of(14, 30), null))).isTrue();
        assertThat(dasDuas.sobrepoe(horario(15))).isFalse(); // 15h comeca quando a das 14h termina
        assertThat(dasDuas.sobrepoe(horario(13))).isFalse();
    }

    @Test
    void sabeSeJaComecou() {
        Agendamento agendamento = horario(14);

        assertThat(agendamento.comecouAte(LocalDateTime.of(DIA, LocalTime.of(13, 59)))).isFalse();
        assertThat(agendamento.comecouAte(LocalDateTime.of(DIA, LocalTime.of(14, 0)))).isTrue();
    }

    @Test
    void observacoesEmBrancoViramNuloEHaLimiteDeTamanho() {
        Agendamento agendamento = horario(14);
        agendamento.alterarObservacoes("   ");
        assertThat(agendamento.getObservacoes()).isNull();

        assertThatThrownBy(() -> agendamento.alterarObservacoes("x".repeat(4001)))
                .hasMessageContaining("4000");
    }
}
