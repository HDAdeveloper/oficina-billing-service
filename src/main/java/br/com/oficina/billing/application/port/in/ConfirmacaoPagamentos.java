package br.com.oficina.billing.application.port.in;

/**
 * Entradas que não vêm de mensagem da Saga: o aviso do provedor (webhook) de que um pagamento mudou e o
 * relógio que vence os links não pagos. Ambas respondem à Saga pelos mesmos eventos de sempre.
 */
public interface ConfirmacaoPagamentos {

    /** Webhook: consulta o pagamento no provedor e, se foi aprovado, confirma o pagamento do orçamento. */
    void pagamentoAtualizado(String idPagamentoExterno);

    /** Aprova o pagamento aguardando (simulador e testes). */
    void aprovarSimulado(String pagamentoId);

    /** Vence os links não pagos cuja validade passou; devolve quantos venceram. */
    int expirarVencidos();

    /** Vence um link agora (simulador e testes). */
    void expirarAgora(String pagamentoId);
}
