package br.com.oficina.billing.application.port.out;

/**
 * Registra um evento para ser enviado ao OS Service. A implementação grava na tabela de outbox, na
 * mesma transação do estado; um publicador separado envia ao SNS (docs/eventos.md, regra 4).
 */
public interface PublicadorEventos {
    void publicar(EventoSaga evento);
}
