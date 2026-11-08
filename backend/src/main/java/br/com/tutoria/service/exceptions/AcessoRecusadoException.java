package br.com.tutoria.service.exceptions;

import org.springframework.http.HttpStatus;

// Recusa com status HTTP proprio e mensagem que pode ser mostrada ao usuario
// (login com Google invalido, conta de outro dominio, cadastro desligado...).
public class AcessoRecusadoException extends RuntimeException {

    private final HttpStatus status;

    public AcessoRecusadoException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
