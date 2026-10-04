package br.com.tutoria.controller;

import br.com.tutoria.model.Agendamento;
import br.com.tutoria.model.Avaliacao;
import br.com.tutoria.model.Tutor;
import br.com.tutoria.service.AgendamentoService;
import br.com.tutoria.service.AvaliacaoService;
import br.com.tutoria.service.TutorService;
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
@RequestMapping("/tutor")
public class TutorController {

    private final TutorService tutorService;
    private final AgendamentoService agendamentoService;
    private final AvaliacaoService avaliacaoService;

    public TutorController(
            TutorService tutorService,
            AgendamentoService agendamentoService,
            AvaliacaoService avaliacaoService) {
        this.tutorService = tutorService;
        this.agendamentoService = agendamentoService;
        this.avaliacaoService = avaliacaoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tutor salvar(@RequestBody Tutor tutor) {
        return tutorService.salvar(tutor);
    }

    @GetMapping
    public List<Tutor> listarTodos() {
        return tutorService.listarTodos();
    }

    @GetMapping("/{id}")
    public Tutor buscarPorId(@PathVariable Long id) {
        return tutorService.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public Tutor atualizar(@PathVariable Long id, @RequestBody Tutor tutor) {
        return tutorService.atualizar(id, tutor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        tutorService.deletar(id);
    }

    @GetMapping("/{id}/agendamentos")
    public List<Agendamento> listarAgendamentos(@PathVariable Long id) {
        return agendamentoService.listarAgendamentosPorTutor(id);
    }

    @GetMapping("/{id}/avaliacoes")
    public List<Avaliacao> listarAvaliacoes(@PathVariable Long id) {
        return avaliacaoService.listarAvaliacoesPorTutor(id);
    }
}
