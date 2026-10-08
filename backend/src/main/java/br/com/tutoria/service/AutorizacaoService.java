package br.com.tutoria.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.enums.ProfileEnum;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;

// Regras de "quem e dono de que". Papeis fixos (como ADMIN) ficam no
// @PreAuthorize dos controllers; as regras que dependem do banco ficam aqui.
// Regra de ouro: a identidade vem sempre do token, nunca do corpo da requisicao.
@Service
public class AutorizacaoService {

    private final EstudanteRepository estudanteRepository;
    private final TutorRepository tutorRepository;

    public AutorizacaoService(EstudanteRepository estudanteRepository, TutorRepository tutorRepository) {
        this.estudanteRepository = estudanteRepository;
        this.tutorRepository = tutorRepository;
    }

    public boolean isAdmin(UserSpringSecurity usuarioLogado) {
        return usuarioLogado.hasRole(ProfileEnum.ADMIN);
    }

    public boolean isUsuario(UserSpringSecurity usuarioLogado, Long usuarioId) {
        return usuarioLogado.getId().equals(usuarioId);
    }

    // Perfil de estudante de quem esta logado; 403 se a pessoa nao for estudante
    @Transactional(readOnly = true)
    public Estudante estudanteLogado(UserSpringSecurity usuarioLogado) {
        return estudanteRepository.findByUsuario_Id(usuarioLogado.getId())
                .orElseThrow(() -> new AccessDeniedException("Apenas estudantes podem realizar esta acao"));
    }

    // Perfil de tutor de quem esta logado; 403 se a pessoa nao for tutor
    @Transactional(readOnly = true)
    public Tutor tutorLogado(UserSpringSecurity usuarioLogado) {
        return tutorRepository.findByUsuario_Id(usuarioLogado.getId())
                .orElseThrow(() -> new AccessDeniedException("Apenas tutores podem realizar esta acao"));
    }

    // Admin ou o proprio estudante; 404 se o estudante nao existir
    @Transactional(readOnly = true)
    public void exigirAcessoAoEstudante(Long estudanteId, UserSpringSecurity usuarioLogado) {
        Estudante estudante = estudanteRepository.findById(estudanteId)
                .orElseThrow(() -> new ObjectNotFoundException("Estudante nao encontrado: " + estudanteId));

        if (!isAdmin(usuarioLogado) && !isUsuario(usuarioLogado, estudante.getUsuario().getId())) {
            throw new AccessDeniedException("Voce so pode acessar os seus proprios dados");
        }
    }

    // Admin ou o proprio tutor; 404 se o tutor nao existir
    @Transactional(readOnly = true)
    public void exigirAcessoAoTutor(Long tutorId, UserSpringSecurity usuarioLogado) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new ObjectNotFoundException("Tutor nao encontrado: " + tutorId));

        if (!isAdmin(usuarioLogado) && !isUsuario(usuarioLogado, tutor.getUsuario().getId())) {
            throw new AccessDeniedException("Voce so pode acessar os seus proprios dados");
        }
    }
}
