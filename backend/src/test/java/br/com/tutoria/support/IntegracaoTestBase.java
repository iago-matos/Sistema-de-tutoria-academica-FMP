package br.com.tutoria.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.tutoria.Security.JWTUtil;
import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.ProfileEnum;
import br.com.tutoria.model.enums.TutorDisciplinaStatusEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.repository.UsuarioRepository;
import br.com.tutoria.service.DisciplinaService;

// Base dos testes de integracao: sobe a aplicacao inteira com o banco H2 e
// desfaz tudo o que cada teste gravou (@Transactional), para um nao afetar o outro.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class IntegracaoTestBase {

    // Criado pelo AdminInicialSeeder a partir do src/test/resources/application.properties
    protected static final String ADMIN_EMAIL = "admin@teste.com";
    protected static final String ADMIN_SENHA = "SenhaAdmin123";
    protected static final String SENHA_PADRAO = "Senha12345";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UsuarioRepository usuarioRepository;
    @Autowired protected EstudanteRepository estudanteRepository;
    @Autowired protected TutorRepository tutorRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JWTUtil jwtUtil;
    @Autowired protected DisciplinaService disciplinaService;

    protected Usuario criarUsuario(String email, UsuarioStatusEnum status, ProfileEnum... perfis) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuario " + email);
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(SENHA_PADRAO));
        usuario.getProfiles().add(ProfileEnum.USER);
        for (ProfileEnum perfil : perfis) {
            usuario.getProfiles().add(perfil);
        }
        usuario.setStatus(status);
        return usuarioRepository.save(usuario);
    }

    protected Estudante criarEstudante(String email) {
        Estudante estudante = new Estudante();
        estudante.setUsuario(criarUsuario(email, UsuarioStatusEnum.ATIVO));
        return estudanteRepository.save(estudante);
    }

    protected Tutor criarTutor(String email) {
        return criarTutorDe(email, "Algoritmos");
    }

    // Tutor ativo com as disciplinas ativas informadas (criadas na lista, se preciso)
    protected Tutor criarTutorDe(String email, String... disciplinas) {
        Tutor tutor = new Tutor();
        tutor.setUsuario(criarUsuario(email, UsuarioStatusEnum.ATIVO));
        tutorRepository.save(tutor);
        for (String nome : disciplinas) {
            disciplinaService.vincular(tutor, disciplinaService.buscarOuCriar(nome), TutorDisciplinaStatusEnum.ATIVA);
        }
        return tutor;
    }

    // Gera um token JWT valido para o usuario, como o login faria
    protected String tokenDe(Usuario usuario) {
        UserSpringSecurity principal = new UserSpringSecurity(
                usuario.getId(), usuario.getEmail(), usuario.getSenha(),
                usuario.getProfiles(), usuario.getStatus());
        return "Bearer " + jwtUtil.generateToken(principal);
    }

    protected String tokenAdmin() {
        return tokenDe(usuarioRepository.findByEmail(ADMIN_EMAIL).orElseThrow());
    }

    protected ResultActions login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "senha", senha))));
    }

    protected String json(Object corpo) throws Exception {
        return objectMapper.writeValueAsString(corpo);
    }
}
