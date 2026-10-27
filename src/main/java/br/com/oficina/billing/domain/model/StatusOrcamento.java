package br.com.oficina.billing.domain.model;

public enum StatusOrcamento {
    /** Criado, aguardando a cobrança. */
    PENDENTE,
    /** Pagamento aprovado. */
    PAGO,
    /** Cancelado antes de ser pago (compensação). */
    CANCELADO,
    /** Estava pago e o valor foi devolvido (compensação). */
    ESTORNADO
}
