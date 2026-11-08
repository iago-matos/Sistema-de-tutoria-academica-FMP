package br.com.tutoria.configs;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.tutoria.model.Disciplina;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.enums.TutorDisciplinaStatusEnum;
import br.com.tutoria.repository.DisciplinaRepository;
import br.com.tutoria.repository.TutorDisciplinaRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.service.DisciplinaService;
import lombok.extern.slf4j.Slf4j;

// Ajustes de banco que o Hibernate (ddl-auto=update) nao faz sozinho: ele cria
// tabelas e colunas novas, mas nunca migra dados nem apaga colunas que sairam
// das entidades. Cada passo pode rodar varias vezes sem efeito.
//
// Roda antes dos outros ApplicationRunner (seeders), que ja gravam nas tabelas.
// Quando o projeto adotar o Flyway, estes passos viram as primeiras migracoes.
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AjusteDeEsquema implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final DisciplinaRepository disciplinaRepository;
    private final TutorDisciplinaRepository tutorDisciplinaRepository;
    private final TutorRepository tutorRepository;
    private final DisciplinaService disciplinaService;
    private final List<String> disciplinasIniciais;

    public AjusteDeEsquema(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            DisciplinaRepository disciplinaRepository,
            TutorDisciplinaRepository tutorDisciplinaRepository,
            TutorRepository tutorRepository,
            DisciplinaService disciplinaService,
            @Value("${app.disciplinas.iniciais:}") String disciplinasIniciais) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.disciplinaRepository = disciplinaRepository;
        this.tutorDisciplinaRepository = tutorDisciplinaRepository;
        this.tutorRepository = tutorRepository;
        this.disciplinaService = disciplinaService;
        this.disciplinasIniciais = Arrays.stream(disciplinasIniciais.split(","))
                .map(String::trim)
                .filter(nome -> !nome.isEmpty())
                .toList();
    }

    @Override
    public void run(ApplicationArguments args) {
        // 08/11/2026: o estudante deixou de informar o dia da semana no cadastro.
        // A coluna era NOT NULL; mantida, impediria criar estudantes.
        jdbcTemplate.execute("ALTER TABLE estudante DROP COLUMN IF EXISTS dia_da_semana");

        // 08/11/2026: a disciplina do tutor deixou de ser texto livre e virou uma
        // lista mantida pela coordenacao, com varias disciplinas por tutor.
        transactionTemplate.executeWithoutResult(status -> {
            criarListaInicial();
            migrarDisciplinaDeTexto();
        });
        jdbcTemplate.execute("ALTER TABLE tutor DROP COLUMN IF EXISTS disciplina");
    }

    // So no primeiro uso: depois, quem mantem a lista e a coordenacao
    private void criarListaInicial() {
        if (disciplinaRepository.count() > 0 || disciplinasIniciais.isEmpty()) {
            return;
        }
        disciplinasIniciais.forEach(disciplinaService::buscarOuCriar);
        log.info("Lista inicial de disciplinas criada: {}", disciplinasIniciais);
    }

    // Cada tutor antigo passa a ter a sua disciplina (do texto) como ATIVA, e as
    // tutorias ja solicitadas ficam registradas com ela. Nomes que diferem so por
    // acento ou maiusculas ("Matematica" e "Matemática") viram a mesma disciplina.
    private void migrarDisciplinaDeTexto() {
        if (!colunaExiste("TUTOR", "DISCIPLINA")) {
            return;
        }
        List<Map<String, Object>> tutores = jdbcTemplate.queryForList("SELECT id, disciplina FROM tutor");
        int migrados = 0;
        for (Map<String, Object> linha : tutores) {
            Long tutorId = ((Number) valor(linha, "id")).longValue();
            Object texto = valor(linha, "disciplina");
            if (texto == null || texto.toString().isBlank()) {
                continue;
            }
            Disciplina disciplina = disciplinaService.buscarOuCriar(texto.toString());
            Tutor tutor = tutorRepository.findById(tutorId).orElseThrow();
            if (tutorDisciplinaRepository.findByTutor_IdAndDisciplina_Id(tutorId, disciplina.getId()).isEmpty()) {
                disciplinaService.vincular(tutor, disciplina, TutorDisciplinaStatusEnum.ATIVA);
            }
            jdbcTemplate.update("""
                    UPDATE agendamento SET disciplina_id = ?
                    WHERE tutor_id = ? AND estudante_id IS NOT NULL AND disciplina_id IS NULL
                    """, disciplina.getId(), tutorId);
            migrados++;
        }
        log.info("Disciplina de texto migrada para a lista em {} tutor(es)", migrados);
    }

    private boolean colunaExiste(String tabela, String coluna) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?",
                Integer.class, tabela, coluna);
        return total != null && total > 0;
    }

    // H2 devolve as colunas em maiusculas; o SQL Server, como foram criadas
    private static Object valor(Map<String, Object> linha, String coluna) {
        return linha.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(coluna))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
