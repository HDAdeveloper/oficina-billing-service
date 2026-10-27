package br.com.oficina.billing.application.port.out;

/** O provedor de pagamento não respondeu. É temporário: a mensagem deve ser tentada de novo. */
public class GatewayPagamentoIndisponivelException extends RuntimeException {
    public GatewayPagamentoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
