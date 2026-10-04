package br.com.tutoria.controller;

import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Avaliacao;
import br.com.tutoria.model.Estudante;
import br.com.tutoria.service.AgendamentoService;
import br.com.tutoria.service.AvaliacaoService;
import br.com.tutoria.service.EstudanteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/estudante")
public class EstudanteController {

    private final EstudanteService estudanteService;
    private final AgendamentoService agendamentoService;
    private final AvaliacaoService avaliacaoService;

    public EstudanteController(
            EstudanteService estudanteService,
            AgendamentoService agendamentoService,
            AvaliacaoService avaliacaoService) {
        this.estudanteService = estudanteService;
        this.agendamentoService = agendamentoService;
        this.avaliacaoService = avaliacaoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Estudante salvar(@RequestBody Estudante estudante) {
        return estudanteService.salvar(estudante);
    }

    @GetMapping
    public List<Estudante> listarTodos() {
        return estudanteService.listarTodos();
    }

    @GetMapping("/{id}")
    public Estudante buscarPorId(@PathVariable Long id) {
        return estudanteService.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public Estudante atualizar(@PathVariable Long id, @RequestBody Estudante estudante) {
        return estudanteService.atualizar(id, estudante);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        estudanteService.deletar(id);
    }

    @GetMapping("/{id}/agendamentos")
    public List<Agendamento> listarAgendamentos(@PathVariable Long id) {
        return agendamentoService.listarAgendamentosPorEstudante(id);
    }

    @GetMapping("/{id}/avaliacoes")
    public List<Avaliacao> listarAvaliacoes(@PathVariable Long id) {
        return avaliacaoService.listarAvaliacoesPorEstudante(id);
    }
}
