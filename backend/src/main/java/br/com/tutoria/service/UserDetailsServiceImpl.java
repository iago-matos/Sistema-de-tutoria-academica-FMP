package br.com.tutoria.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.repository.UsuarioRepository;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UserDetailsServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String emailNormalizado = UsuarioService.normalizarEmail(email);

        Usuario usuario = usuarioRepository.findByEmail(emailNormalizado)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Usuario nao encontrado: " + emailNormalizado));

        return new UserSpringSecurity(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getSenha(),
                usuario.getProfiles(),
                usuario.getStatus()
        );
    }
}
