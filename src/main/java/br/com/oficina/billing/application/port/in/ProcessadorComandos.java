package br.com.oficina.billing.application.port.in;

/**
 * Porta de entrada da mensageria. Cada chamada é uma transação: o estado do Billing, a marca de
 * "mensagem já processada" e os eventos de resposta (outbox) são confirmados juntos ou nada é.
 */
public interface ProcessadorComandos {

    /**
     * Executa o comando. Mensagem repetida (mesmo {@code messageId}) é ignorada.
     * Falhas temporárias (gateway fora do ar) lançam exceção: a mensagem volta à fila e, depois
     * de 3 tentativas, vai para a DLQ.
     */
    void processar(ContextoMensagem contexto, ComandoBilling comando);
}
