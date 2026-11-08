package br.com.tutoria.configs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Disciplina;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.support.IntegracaoTestBase;

// Bancos criados antes de 08/11/2026 tem colunas que sairam das entidades:
// estudante.dia_da_semana (NOT NULL) e tutor.disciplina (texto livre)
class AjusteDeEsquemaTest extends IntegracaoTestBase {

    @Autowired private AjusteDeEsquema ajusteDeEsquema;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AgendamentoRepository agendamentoRepository;

    private boolean colunaExiste(String tabela, String coluna) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?",
                Integer.class, tabela, coluna);
        return total != null && total > 0;
    }

    @Test
    void removeAColunaAntigaDoDiaDaSemana() {
        jdbcTemplate.execute("ALTER TABLE estudante ADD COLUMN dia_da_semana VARCHAR(255) DEFAULT 'Segunda' NOT NULL");
        assertThat(colunaExiste("ESTUDANTE", "DIA_DA_SEMANA")).isTrue();

        ajusteDeEsquema.run(null);

        assertThat(colunaExiste("ESTUDANTE", "DIA_DA_SEMANA")).isFalse();
        // Depois do ajuste, estudante novo e gravado sem o campo
        assertThat(criarEstudante("depois-do-ajuste@teste.com").getId()).isNotNull();
    }

    @Test
    void podeRodarDeNovoSemAsColunasAntigas() {
        assertThat(colunaExiste("ESTUDANTE", "DIA_DA_SEMANA")).isFalse();
        assertThat(colunaExiste("TUTOR", "DISCIPLINA")).isFalse();

        assertThatCode(() -> ajusteDeEsquema.run(null)).doesNotThrowAnyException();
    }

    // Fora da transacao do teste: no H2, o ALTER TABLE confirma a transacao em
    // andamento, entao os dados sao criados e apagados aqui mesmo.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void migraADisciplinaDeTextoParaALista() {
        jdbcTemplate.execute("ALTER TABLE tutor ADD COLUMN disciplina VARCHAR(255)");
        Usuario usuarioTutor = criarUsuario("tutor-legado@teste.com", UsuarioStatusEnum.ATIVO);
        Usuario usuarioAluno = criarUsuario("aluno-legado@teste.com", UsuarioStatusEnum.ATIVO);
        Tutor tutor = new Tutor();
        tutor.setUsuario(usuarioTutor);
        tutorRepository.save(tutor);
        Estudante aluno = new Estudante();
        aluno.setUsuario(usuarioAluno);
        estudanteRepository.save(aluno);
        Agendamento solicitada = Agendamento.disponibilizar(tutor, LocalDate.now().plusDays(2), LocalTime.of(9, 0), null);
        solicitada.agendar(aluno);
        agendamentoRepository.save(solicitada);
        Agendamento livre = agendamentoRepository.save(
                Agendamento.disponibilizar(tutor, LocalDate.now().plusDays(2), LocalTime.of(11, 0), null));
        // Texto antigo, digitado sem acento e com espacos
        jdbcTemplate.update("UPDATE tutor SET disciplina = ? WHERE id = ?", "  matematica ", tutor.getId());

        try {
            ajusteDeEsquema.run(null);

            Disciplina matematica = disciplinaService.buscarPorNome("Matemática").orElseThrow();
            assertThat(disciplinaService.ativasDoTutor(tutor.getId())).containsExactly(matematica);
            // Virou a disciplina da lista, sem criar uma "matematica" repetida
            assertThat(disciplinaService.listar()).filteredOn(d -> d.nome().equalsIgnoreCase("matematica")).isEmpty();
            assertThat(disciplinaDoAgendamento(solicitada.getId())).isEqualTo(matematica.getId());
            assertThat(disciplinaDoAgendamento(livre.getId())).isNull();
            assertThat(colunaExiste("TUTOR", "DISCIPLINA")).isFalse();
        } finally {
            jdbcTemplate.execute("ALTER TABLE tutor DROP COLUMN IF EXISTS disciplina");
            jdbcTemplate.update("DELETE FROM agendamento WHERE tutor_id = ?", tutor.getId());
            jdbcTemplate.update("DELETE FROM tutor_disciplina WHERE tutor_id = ?", tutor.getId());
            jdbcTemplate.update("DELETE FROM tutor WHERE id = ?", tutor.getId());
            jdbcTemplate.update("DELETE FROM estudante WHERE id = ?", aluno.getId());
            jdbcTemplate.update("DELETE FROM usuario_profiles WHERE usuario_id IN (?, ?)", usuarioTutor.getId(), usuarioAluno.getId());
            jdbcTemplate.update("DELETE FROM usuario WHERE id IN (?, ?)", usuarioTutor.getId(), usuarioAluno.getId());
        }
    }

    private Long disciplinaDoAgendamento(Long agendamentoId) {
        return jdbcTemplate.queryForObject("SELECT disciplina_id FROM agendamento WHERE id = ?", Long.class, agendamentoId);
    }
}
