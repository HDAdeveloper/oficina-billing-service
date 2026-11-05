package br.com.oficina.billing.application.port.out;

/** O id de pagamento informado não tem o formato do provedor (no Mercado Pago, só dígitos). */
public class IdPagamentoExternoInvalidoException extends RuntimeException {

    public IdPagamentoExternoInvalidoException() {
        super("Id de pagamento do provedor deve ser numérico");
    }
}
