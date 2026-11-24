package br.com.tutoria.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.Security.TokenRedefinicaoSenha;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.UsuarioRepository;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;
import lombok.extern.slf4j.Slf4j;

// RF02 - Recuperacao de senha, para quem entra com e-mail e senha
// (estudantes e tutores normalmente entram com o Google e nao precisam disto).
//
// Dois caminhos, os dois terminam no mesmo link "redefinir-senha.html?token=...":
// 1. "Esqueci minha senha" na tela de login: o link vai por e-mail. So funciona
//    com um servidor SMTP configurado (MAIL_HOST etc., veja o .env.example).
// 2. A coordenacao gera o link na tela de Usuarios e o entrega a pessoa.
//    Funciona sem servidor de e-mail.
@Slf4j
@Service
public class RedefinicaoSenhaService {

    // Um e-mail de redefinicao por conta a cada 2 minutos (evita encher a caixa de alguem)
    private static final Duration INTERVALO_ENTRE_EMAILS = Duration.ofMinutes(2);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRedefinicaoSenha tokenRedefinicaoSenha;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final TaskExecutor executor;
    private final Clock clock;
    private final String mailHost;
    private final String remetente;
    private final String frontendUrl;
    private final Map<Long, Instant> ultimoEnvio = new ConcurrentHashMap<>();

    public RedefinicaoSenhaService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            TokenRedefinicaoSenha tokenRedefinicaoSenha,
            ObjectProvider<JavaMailSender> mailSender,
            TaskExecutor executor,
            Clock clock,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${app.mail.remetente:}") String remetente,
            @Value("${app.frontend-url:}") String frontendUrl) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenRedefinicaoSenha = tokenRedefinicaoSenha;
        this.mailSender = mailSender;
        this.executor = executor;
        this.clock = clock;
        this.mailHost = mailHost.trim();
        this.remetente = remetente.trim();
        this.frontendUrl = frontendUrl.trim().replaceAll("/+$", "");
    }

    // A tela de login so oferece "Esqueci minha senha" por e-mail quando isto e true
    public boolean isEnvioPorEmailHabilitado() {
        return !mailHost.isEmpty() && !remetente.isEmpty() && !frontendUrl.isEmpty()
                && mailSender.getIfAvailable() != null;
    }

    // "Esqueci minha senha". A resposta e sempre a mesma, exista ou nao a conta:
    // assim ninguem usa esta tela para descobrir quais e-mails estao cadastrados.
    // O envio roda em segundo plano para o tempo de resposta tambem nao denunciar.
    @Transactional(readOnly = true)
    public void solicitarPorEmail(String email) {
        if (!isEnvioPorEmailHabilitado()) {
            return;
        }
        usuarioRepository.findByEmail(UsuarioService.normalizarEmail(email))
                .filter(usuario -> usuario.getStatus() == UsuarioStatusEnum.ATIVO)
                .filter(this::podeEnviarAgora)
                .ifPresent(usuario -> {
                    String link = frontendUrl + "/redefinir-senha.html?token="
                            + tokenRedefinicaoSenha.gerar(usuario, clock.instant());
                    String nome = usuario.getNome();
                    String destino = usuario.getEmail();
                    executor.execute(() -> enviarEmail(destino, nome, link));
                });
    }

    // A coordenacao gera o link para entregar a pessoa (sem depender de e-mail).
    // Devolve so o token; a tela monta o endereco a partir de onde esta publicada.
    @Transactional(readOnly = true)
    public Map<String, Object> gerarParaUsuario(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Usuario nao encontrado: " + usuarioId));
        if (usuario.getStatus() != UsuarioStatusEnum.ATIVO) {
            throw new IllegalArgumentException("Só é possível redefinir a senha de contas ativas");
        }
        Instant agora = clock.instant();
        return Map.of(
                "token", tokenRedefinicaoSenha.gerar(usuario, agora),
                "expiraEm", LocalDateTime.ofInstant(agora.plus(TokenRedefinicaoSenha.VALIDADE), clock.getZone()));
    }

    // Troca a senha a partir do link. Depois disso o mesmo link deixa de valer.
    @Transactional
    public void redefinir(String token, String novaSenha) {
        Long usuarioId = tokenRedefinicaoSenha.validar(token,
                id -> usuarioRepository.findById(id).orElse(null));
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();

        if (usuario.getStatus() != UsuarioStatusEnum.ATIVO) {
            throw new IllegalArgumentException("Esta conta não está ativa. Procure a coordenação.");
        }
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
        ultimoEnvio.remove(usuarioId);
        log.info("Senha redefinida pelo link de recuperacao: usuario {}", usuarioId);
    }

    private boolean podeEnviarAgora(Usuario usuario) {
        Instant agora = clock.instant();
        Instant anterior = ultimoEnvio.get(usuario.getId());
        if (anterior != null && anterior.plus(INTERVALO_ENTRE_EMAILS).isAfter(agora)) {
            return false;
        }
        ultimoEnvio.put(usuario.getId(), agora);
        return true;
    }

    private void enviarEmail(String destino, String nome, String link) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(destino);
        mensagem.setSubject("Tutoria Acadêmica FMP - redefinição de senha");
        mensagem.setText("Olá, " + nome + ".\n\n"
                + "Recebemos um pedido para redefinir a senha da sua conta na Tutoria Acadêmica.\n"
                + "Para criar uma nova senha, abra o link abaixo (ele vale por "
                + TokenRedefinicaoSenha.VALIDADE.toMinutes() + " minutos e funciona uma única vez):\n\n"
                + link + "\n\n"
                + "Se não foi você, ignore este e-mail: sua senha atual continua valendo.\n");
        try {
            mailSender.getObject().send(mensagem);
            log.info("E-mail de redefinicao de senha enviado");
        } catch (RuntimeException e) {
            log.error("Falha ao enviar o e-mail de redefinicao de senha: {}", e.getMessage());
        }
    }
}
