package br.com.oficina.billing.domain.exception;

public class RecursoNaoEncontradoException extends RegraDeNegocioException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
