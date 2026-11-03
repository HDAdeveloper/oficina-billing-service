package br.com.oficina.billing.domain.model;

public enum StatusPagamento {
    /** Link de pagamento enviado ao cliente; ainda não houve pagamento nem expirou. */
    AGUARDANDO,
    APROVADO,
    RECUSADO,
    /** O link venceu (ou o orçamento foi cancelado) sem pagamento. */
    EXPIRADO,
    ESTORNADO
}
