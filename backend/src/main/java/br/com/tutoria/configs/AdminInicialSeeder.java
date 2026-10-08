package br.com.tutoria.configs;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.ProfileEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.UsuarioRepository;
import br.com.tutoria.service.UsuarioService;
import lombok.extern.slf4j.Slf4j;

// Garante, toda vez que a aplicacao sobe, que os e-mails de ADMIN_EMAILS
// existam como administradores ativos. Os e-mails e a senha inicial vem de
// variaveis de ambiente: nada disso fica no codigo, que e publico.
@Slf4j
@Component
public class AdminInicialSeeder implements ApplicationRunner {

    private static final int TAMANHO_MINIMO_SENHA = 8;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String emails;
    private final String senhaInicial;

    public AdminInicialSeeder(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.emails:}") String emails,
            @Value("${app.admin.senha-inicial:}") String senhaInicial) {

        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emails = emails;
        this.senhaInicial = senhaInicial;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<String> listaEmails = Arrays.stream(emails.split(","))
                .map(UsuarioService::normalizarEmail)
                .filter(email -> !email.isEmpty())
                .toList();

        if (listaEmails.isEmpty()) {
            log.warn("ADMIN_EMAILS nao configurado: nenhum administrador inicial foi criado");
            return;
        }

        listaEmails.forEach(this::garantirAdministrador);
    }

    private void garantirAdministrador(String email) {
        usuarioRepository.findByEmail(email).ifPresentOrElse(
                existente -> {
                    boolean jaEAdminAtivo = existente.getProfiles().contains(ProfileEnum.ADMIN)
                            && existente.getStatus() == UsuarioStatusEnum.ATIVO;

                    // Ja existe (por exemplo, cadastrou-se antes como estudante):
                    // promove sem alterar a senha que a pessoa ja usa
                    if (!jaEAdminAtivo) {
                        existente.getProfiles().add(ProfileEnum.ADMIN);
                        existente.setStatus(UsuarioStatusEnum.ATIVO);
                        usuarioRepository.save(existente);
                        log.info("Usuario existente promovido a administrador: {}", email);
                    }
                },
                () -> {
                    // A senha inicial so e exigida quando ha uma conta nova para criar
                    if (senhaInicial.length() < TAMANHO_MINIMO_SENHA) {
                        throw new IllegalStateException("ADMIN_SENHA_INICIAL precisa ter no minimo "
                                + TAMANHO_MINIMO_SENHA + " caracteres para criar o administrador " + email);
                    }

                    Usuario admin = new Usuario();
                    admin.setNome("Administrador");
                    admin.setEmail(email);
                    admin.setSenha(passwordEncoder.encode(senhaInicial));
                    admin.getProfiles().add(ProfileEnum.USER);
                    admin.getProfiles().add(ProfileEnum.ADMIN);
                    admin.setStatus(UsuarioStatusEnum.ATIVO);
                    usuarioRepository.save(admin);
                    log.info("Administrador inicial criado: {}", email);
                });
    }
}
