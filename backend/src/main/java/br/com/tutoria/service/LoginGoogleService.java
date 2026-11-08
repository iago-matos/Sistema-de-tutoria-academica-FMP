package br.com.tutoria.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.Security.GoogleTokenVerifier;
import br.com.tutoria.Security.GoogleTokenVerifier.IdentidadeGoogle;
import br.com.tutoria.Security.JWTUtil;
import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.dto.LoginGoogleDTO;
import br.com.tutoria.dto.LoginGoogleRespostaDTO.Situacao;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.model.enums.UsuarioTipoEnum;
import br.com.tutoria.repository.UsuarioRepository;
import br.com.tutoria.service.exceptions.AcessoRecusadoException;

// Login com a conta Google institucional.
//
// - Conta ja existente com o mesmo e-mail (inclusive criada com senha): entra.
// - Primeiro acesso: pede o perfil. Estudante entra na hora, porque o e-mail
//   institucional ja comprova o vinculo com a faculdade. Tutor fica PENDENTE
//   ate um administrador aprovar.
// - Conta pendente, recusada ou inativa: 403 com o motivo, como no login com senha.
@Service
public class LoginGoogleService {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final JWTUtil jwtUtil;

    public LoginGoogleService(
            GoogleTokenVerifier googleTokenVerifier,
            UsuarioRepository usuarioRepository,
            UsuarioService usuarioService,
            JWTUtil jwtUtil) {

        this.googleTokenVerifier = googleTokenVerifier;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.jwtUtil = jwtUtil;
    }

    public record Resultado(Situacao situacao, String token, String nome, String email) { }

    @Transactional
    public Resultado entrar(LoginGoogleDTO dados) {
        IdentidadeGoogle identidade = googleTokenVerifier.verificar(dados.credential());

        Optional<Usuario> existente = usuarioRepository.findByEmail(identidade.email());
        if (existente.isPresent()) {
            return autenticar(existente.get());
        }

        if (dados.tipo() == null) {
            return new Resultado(Situacao.CADASTRO_NECESSARIO, null, identidade.nome(), identidade.email());
        }

        UsuarioStatusEnum status = dados.tipo() == UsuarioTipoEnum.ESTUDANTE
                ? UsuarioStatusEnum.ATIVO
                : UsuarioStatusEnum.PENDENTE;

        // Senha aleatoria que ninguem conhece: quem entra pelo Google so entra pelo Google
        Usuario novo = usuarioService.criarComPerfil(identidade.nome(), identidade.email(),
                UUID.randomUUID().toString(), dados.tipo(), dados.disciplinaId(), status);

        if (status == UsuarioStatusEnum.ATIVO) {
            return autenticar(novo);
        }
        return new Resultado(Situacao.AGUARDANDO_APROVACAO, null, novo.getNome(), novo.getEmail());
    }

    private Resultado autenticar(Usuario usuario) {
        UserSpringSecurity principal = new UserSpringSecurity(
                usuario.getId(), usuario.getEmail(), usuario.getSenha(),
                usuario.getProfiles(), usuario.getStatus());

        if (!principal.isEnabled()) {
            throw new AcessoRecusadoException(HttpStatus.FORBIDDEN,
                    UserSpringSecurity.motivoDoBloqueio(usuario.getStatus()));
        }

        return new Resultado(Situacao.AUTENTICADO, jwtUtil.generateToken(principal),
                usuario.getNome(), usuario.getEmail());
    }
}
