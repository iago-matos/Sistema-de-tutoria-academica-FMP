package br.com.tutoria.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.Usuario;
import br.com.tutoria.model.enums.AgendamentoStatusEnum;
import br.com.tutoria.model.enums.ProfileEnum;
import br.com.tutoria.model.enums.UsuarioStatusEnum;
import br.com.tutoria.repository.AgendamentoRepository;
import br.com.tutoria.repository.EstudanteRepository;
import br.com.tutoria.repository.TutorRepository;
import br.com.tutoria.repository.UsuarioRepository;
import br.com.tutoria.service.exceptions.ObjectNotFoundException;

// Inativacao de um tutor ou estudante pela coordenacao (admin).
//
// Nada e apagado: a conta fica INATIVA (sem login, e o token em uso deixa de
// valer) e o historico continua no banco: sessoes passadas, avaliacoes e as
// mensagens do chat. Quem conversava com o usuario ainda le o historico, mas
// nao consegue mais enviar mensagens para ele (regra em Conversa.enviar).
//
// So os compromissos futuros sao desfeitos, para ninguem ficar esperando
// alguem que nao vai aparecer:
// Tutor:     os horarios futuros dele sao cancelados (livres ou ja reservados).
// Estudante: as reservas futuras dele voltam a ficar DISPONIVEIS para outros.
//
// Tutores inativos ja somem da busca e da lista de horarios disponiveis,
// porque essas consultas filtram por usuarios ATIVOS.
@Service
public class InativacaoDeContaService {

    private static final Set<AgendamentoStatusEnum> COMPROMISSOS_EM_ABERTO = Set.of(
            AgendamentoStatusEnum.DISPONIVEL, AgendamentoStatusEnum.PENDENTE, AgendamentoStatusEnum.CONFIRMADO);

    private final UsuarioRepository usuarioRepository;
    private final TutorRepository tutorRepository;
    private final EstudanteRepository estudanteRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final Clock clock;

    public InativacaoDeContaService(
            UsuarioRepository usuarioRepository,
            TutorRepository tutorRepository,
            EstudanteRepository estudanteRepository,
            AgendamentoRepository agendamentoRepository,
            Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.tutorRepository = tutorRepository;
        this.estudanteRepository = estudanteRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.clock = clock;
    }

    @Transactional
    public void inativarConta(Long usuarioId, UserSpringSecurity usuarioLogado) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ObjectNotFoundException("Usuario nao encontrado: " + usuarioId));

        if (usuarioLogado.getId().equals(usuarioId)) {
            throw new IllegalArgumentException("Você não pode inativar a própria conta");
        }
        // Administradores sao definidos por ADMIN_EMAILS e reativados ao subir a aplicacao
        if (usuario.getProfiles().contains(ProfileEnum.ADMIN)) {
            throw new IllegalArgumentException(
                    "Administradores não podem ser inativados por aqui. Remova o e-mail de ADMIN_EMAILS.");
        }
        // Cadastro pendente se recusa; inativar e reativar serviria de atalho para aprovar
        if (usuario.getStatus() != UsuarioStatusEnum.ATIVO) {
            throw new IllegalArgumentException("Apenas contas ativas podem ser inativadas");
        }

        LocalDateTime agora = LocalDateTime.now(clock);
        tutorRepository.findByUsuario_Id(usuarioId).ifPresent(tutor -> cancelarHorariosFuturos(tutor, agora));
        estudanteRepository.findByUsuario_Id(usuarioId).ifPresent(estudante -> liberarReservasFuturas(estudante, agora));

        usuario.setStatus(UsuarioStatusEnum.INATIVO);
        usuarioRepository.save(usuario);
    }

    private void cancelarHorariosFuturos(Tutor tutor, LocalDateTime agora) {
        for (Agendamento agendamento : agendamentoRepository.findByTutor_Id(tutor.getId())) {
            if (emAbertoNoFuturo(agendamento, agora)) {
                agendamento.cancelar();
            }
        }
    }

    private void liberarReservasFuturas(Estudante estudante, LocalDateTime agora) {
        for (Agendamento agendamento : agendamentoRepository.findByEstudante_Id(estudante.getId())) {
            if (emAbertoNoFuturo(agendamento, agora)) {
                agendamento.desistir(); // o horario volta para a agenda do tutor
            }
        }
    }

    private static boolean emAbertoNoFuturo(Agendamento agendamento, LocalDateTime agora) {
        return !agendamento.comecouAte(agora) && COMPROMISSOS_EM_ABERTO.contains(agendamento.getStatus());
    }
}
