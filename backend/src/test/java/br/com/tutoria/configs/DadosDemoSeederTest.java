package br.com.tutoria.configs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.enums.AgendamentoStatusEnum;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.repository.AvaliacaoRepository;
import br.com.tutoria.support.IntegracaoTestBase;

class DadosDemoSeederTest extends IntegracaoTestBase {

    @Autowired private AgendamentoRepository agendamentoRepository;
    @Autowired private AvaliacaoRepository avaliacaoRepository;
    @Autowired private Clock clock;

    private DadosDemoSeeder seeder(boolean ativo, String senha) {
        return seeder(ativo, senha, "");
    }

    private DadosDemoSeeder seeder(boolean ativo, String senha, String estudanteExtra) {
        return new DadosDemoSeeder(usuarioRepository, estudanteRepository, tutorRepository,
                agendamentoRepository, avaliacaoRepository, passwordEncoder, clock, disciplinaService,
                ativo, senha, estudanteExtra);
    }

    @Test
    void contaDoDesenvolvedorGanhaAsMesmasTutoriasUmaVezSo() {
        Estudante minhaConta = criarEstudante("eu@teste.com");

        seeder(true, "SenhaDemo123", "EU@teste.com").run(null);
        seeder(true, "SenhaDemo123", "eu@teste.com").run(null);

        assertThat(agendamentoRepository.findByEstudante_Id(minhaConta.getId())).hasSize(7);
        // Os tutores demo sao os mesmos do estudante demo, nao duplicados
        assertThat(usuarioRepository.findByEmail("tutor.ana.demo@exemplo.com")).isPresent();
        assertThat(tutorRepository.findAll().stream()
                .filter(t -> t.getUsuario().getEmail().equals("tutor.ana.demo@exemplo.com"))).hasSize(1);
    }

    @Test
    void desligadoNaoCriaNada() {
        seeder(false, "").run(null);

        assertThat(usuarioRepository.findByEmail(DadosDemoSeeder.EMAIL_ESTUDANTE)).isEmpty();
    }

    @Test
    void ligadoCriaEstudanteComTutoriasParaAvaliarERodaUmaVezSo() throws Exception {
        seeder(true, "SenhaDemo123").run(null);
        seeder(true, "SenhaDemo123").run(null);

        Estudante estudante = estudanteRepository.findAll().stream()
                .filter(e -> e.getUsuario().getEmail().equals(DadosDemoSeeder.EMAIL_ESTUDANTE))
                .findFirst().orElseThrow();

        var tutorias = agendamentoRepository.findByEstudante_Id(estudante.getId());
        assertThat(tutorias).hasSize(7);
        assertThat(tutorias).filteredOn(a -> a.getStatus() == AgendamentoStatusEnum.PENDENTE).hasSize(1);
        assertThat(avaliacaoRepository.findByEstudante_Id(estudante.getId())).hasSize(1);

        // O estudante demo entra com e-mail e senha
        login(DadosDemoSeeder.EMAIL_ESTUDANTE, "SenhaDemo123")
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    @Test
    void ligadoSemSenhaFalhaAoSubir() {
        assertThatThrownBy(() -> seeder(true, "curta").run(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
