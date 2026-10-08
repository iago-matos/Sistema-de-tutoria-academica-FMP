package br.com.tutoria.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.dto.AlterarSenhaDTO;
import br.com.tutoria.dto.AtualizarUsuarioDTO;
import br.com.tutoria.dto.CadastroRequestDTO;
import br.com.tutoria.dto.UsuarioMeDTO;
import br.com.tutoria.dto.UsuarioResumoDTO;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.service.UsuarioService;
import jakarta.validation.Valid;

@RestController
@RequestMapping({"/usuario", "/user"})
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // Público — cadastro de novos usuários (liberado no SecurityConfig).
    // A conta nasce PENDENTE até um administrador aprovar.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResumoDTO cadastrar(@Valid @RequestBody CadastroRequestDTO cadastro) {
        return usuarioService.cadastrar(cadastro);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Usuario> listarTodos() {
        return usuarioService.listarTodos();
    }

    @GetMapping("/pendentes")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UsuarioResumoDTO> listarPendentes() {
        return usuarioService.listarPendentes();
    }

    @PutMapping("/{id}/aprovar")
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioResumoDTO aprovar(@PathVariable Long id) {
        return usuarioService.aprovar(id);
    }

    @PutMapping("/{id}/recusar")
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioResumoDTO recusar(@PathVariable Long id) {
        return usuarioService.recusar(id);
    }

    @GetMapping("/me")
    public UsuarioMeDTO buscarMe(@AuthenticationPrincipal UserSpringSecurity user) {
        return usuarioService.buscarMe(user.getId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Usuario buscarPorId(@PathVariable Long id) {
        return usuarioService.buscarPorId(id);
    }

    // Nome e e-mail. A senha nao muda por aqui (antes, editar o nome pela tela
    // de usuarios do admin redefinia a senha para "placeholder").
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #user.id == #id")
    public Usuario atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarUsuarioDTO dados,
            @AuthenticationPrincipal UserSpringSecurity user) {
        return usuarioService.atualizar(id, dados);
    }

    // O proprio usuario troca a senha, confirmando a senha atual
    @PutMapping("/me/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterarSenha(
            @Valid @RequestBody AlterarSenhaDTO dados,
            @AuthenticationPrincipal UserSpringSecurity user) {
        usuarioService.alterarSenha(user.getId(), dados);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deletar(@PathVariable Long id) {
        usuarioService.deletar(id);
    }
}
