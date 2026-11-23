package br.com.tutoria.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.tutoria.Security.UserSpringSecurity;
import br.com.tutoria.dto.CandidaturaTutorDTO;
import br.com.tutoria.dto.DisciplinaDTO;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.model.TutorDisciplina;
import br.com.tutoria.model.enums.TutorDisciplinaStatusEnum;
import br.com.tutoria.repository.TutorDisciplinaRepository;
import br.com.tutoria.repository.TutorRepository;

// Estudante que se candidata a tutor, sem criar outra conta.
//
// Perfis sao associacoes do Usuario (diagrama de classes): a mesma conta ganha
// um perfil Tutor, com a disciplina escolhida como TutorDisciplina PENDENTE.
// A coordenacao decide em "Aprovações > Pedidos de disciplina", o mesmo fluxo
// das disciplinas novas dos tutores. Ate la o tutor nao e "ativo"
// (DisciplinaService.isTutorAtivo): nao aparece na busca nem abre horarios.
//
// Nao da para usar o status da conta (Usuario.status = PENDENTE), como no
// cadastro de tutor, porque isso bloquearia o login do estudante.
@Service
public class CandidaturaTutorService {

    private final AutorizacaoService autorizacaoService;
    private final TutorRepository tutorRepository;
    private final TutorDisciplinaRepository tutorDisciplinaRepository;
    private final DisciplinaService disciplinaService;

    public CandidaturaTutorService(
            AutorizacaoService autorizacaoService,
            TutorRepository tutorRepository,
            TutorDisciplinaRepository tutorDisciplinaRepository,
            DisciplinaService disciplinaService) {
        this.autorizacaoService = autorizacaoService;
        this.tutorRepository = tutorRepository;
        this.tutorDisciplinaRepository = tutorDisciplinaRepository;
        this.disciplinaService = disciplinaService;
    }

    @Transactional(readOnly = true)
    public CandidaturaTutorDTO situacao(UserSpringSecurity usuarioLogado) {
        Estudante estudante = autorizacaoService.estudanteLogado(usuarioLogado);
        Optional<Tutor> tutor = tutorRepository.findByUsuario_Id(estudante.getUsuario().getId());
        if (tutor.isEmpty()) {
            return CandidaturaTutorDTO.nenhuma();
        }
        if (disciplinaService.isTutorAtivo(tutor.get().getId())) {
            return CandidaturaTutorDTO.aprovada();
        }

        // Um pedido pendente vale mais que recusas antigas; entre recusas, a mais recente
        List<TutorDisciplina> pedidos = tutorDisciplinaRepository.findByTutorId(tutor.get().getId());
        return pedidos.stream()
                .filter(p -> p.getStatus() == TutorDisciplinaStatusEnum.PENDENTE)
                .findFirst()
                .or(() -> pedidos.stream()
                        .filter(p -> p.getStatus() == TutorDisciplinaStatusEnum.RECUSADA)
                        .max(Comparator.comparing(TutorDisciplina::getSolicitadaEm)))
                .map(p -> new CandidaturaTutorDTO(p.getStatus() == TutorDisciplinaStatusEnum.PENDENTE
                        ? "PENDENTE" : "RECUSADA", DisciplinaDTO.de(p.getDisciplina()), p.getSolicitadaEm()))
                .orElse(CandidaturaTutorDTO.nenhuma());
    }

    // Envia a candidatura (ou uma nova, depois de recusada)
    @Transactional
    public CandidaturaTutorDTO candidatar(Long disciplinaId, UserSpringSecurity usuarioLogado) {
        Estudante estudante = autorizacaoService.estudanteLogado(usuarioLogado);
        CandidaturaTutorDTO atual = situacao(usuarioLogado);
        if ("APROVADA".equals(atual.situacao())) {
            throw new IllegalArgumentException("Você já é tutor. Peça outras disciplinas na área do tutor.");
        }
        if ("PENDENTE".equals(atual.situacao())) {
            throw new IllegalArgumentException("Sua candidatura já está aguardando a coordenação");
        }

        Tutor tutor = tutorRepository.findByUsuario_Id(estudante.getUsuario().getId()).orElseGet(() -> {
            Tutor novo = new Tutor();
            novo.setUsuario(estudante.getUsuario());
            return tutorRepository.save(novo);
        });
        disciplinaService.pedir(tutor, disciplinaId);
        return situacao(usuarioLogado);
    }

    // Desiste da candidatura pendente
    @Transactional
    public void cancelar(UserSpringSecurity usuarioLogado) {
        Estudante estudante = autorizacaoService.estudanteLogado(usuarioLogado);
        Tutor tutor = tutorRepository.findByUsuario_Id(estudante.getUsuario().getId())
                .filter(t -> !disciplinaService.isTutorAtivo(t.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Você não tem candidatura pendente"));

        List<TutorDisciplina> pendentes = tutorDisciplinaRepository.findByTutorId(tutor.getId()).stream()
                .filter(p -> p.getStatus() == TutorDisciplinaStatusEnum.PENDENTE)
                .toList();
        if (pendentes.isEmpty()) {
            throw new IllegalArgumentException("Você não tem candidatura pendente");
        }
        pendentes.forEach(p -> disciplinaService.remover(tutor, p.getId()));
    }
}
