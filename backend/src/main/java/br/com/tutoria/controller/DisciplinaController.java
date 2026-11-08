package br.com.tutoria.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.tutoria.dto.DisciplinaDTO;
import br.com.tutoria.dto.NomeDisciplinaDTO;
import br.com.tutoria.dto.PedidoDisciplinaDTO;
import br.com.tutoria.dto.TutorDisciplinaDTO;
import br.com.tutoria.service.DisciplinaService;
import jakarta.validation.Valid;

// Lista de disciplinas (mantida pela coordenacao) e pedidos de disciplina dos tutores
@RestController
@RequestMapping("/disciplina")
public class DisciplinaController {

    private final DisciplinaService disciplinaService;

    public DisciplinaController(DisciplinaService disciplinaService) {
        this.disciplinaService = disciplinaService;
    }

    // Publica: o tutor escolhe a disciplina no primeiro acesso, antes de ter login
    @GetMapping
    public List<DisciplinaDTO> listar() {
        return disciplinaService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DisciplinaDTO criar(@Valid @RequestBody NomeDisciplinaDTO dados) {
        return disciplinaService.criar(dados.nome());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DisciplinaDTO renomear(@PathVariable Long id, @Valid @RequestBody NomeDisciplinaDTO dados) {
        return disciplinaService.renomear(id, dados.nome());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void excluir(@PathVariable Long id) {
        disciplinaService.excluir(id);
    }

    // ── Pedidos de disciplina nova dos tutores ───────────────────────────────

    @GetMapping("/pedidos")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PedidoDisciplinaDTO> pedidosPendentes() {
        return disciplinaService.pedidosPendentes();
    }

    @PutMapping("/pedidos/{id}/aprovar")
    @PreAuthorize("hasRole('ADMIN')")
    public TutorDisciplinaDTO aprovar(@PathVariable Long id) {
        return disciplinaService.aprovar(id);
    }

    @PutMapping("/pedidos/{id}/recusar")
    @PreAuthorize("hasRole('ADMIN')")
    public TutorDisciplinaDTO recusar(@PathVariable Long id) {
        return disciplinaService.recusar(id);
    }
}
