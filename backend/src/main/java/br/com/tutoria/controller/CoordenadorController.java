package br.com.tutoria.controller;

import br.com.tutoria.model.Coordenador;
import br.com.tutoria.service.CoordenadorService;
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
@RequestMapping("/coordenador")
public class CoordenadorController {

    private final CoordenadorService coordenadorService;

    public CoordenadorController(CoordenadorService coordenadorService) {
        this.coordenadorService = coordenadorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Coordenador salvar(@RequestBody Coordenador coordenador) {
        return coordenadorService.salvar(coordenador);
    }

    @GetMapping
    public List<Coordenador> listarTodos() {
        return coordenadorService.listarTodos();
    }

    @GetMapping("/{id}")
    public Coordenador buscarPorId(@PathVariable Long id) {
        return coordenadorService.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public Coordenador atualizar(@PathVariable Long id, @RequestBody Coordenador coordenador) {
        return coordenadorService.atualizar(id, coordenador);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        coordenadorService.deletar(id);
    }
}
