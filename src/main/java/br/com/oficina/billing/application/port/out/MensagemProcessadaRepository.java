package br.com.oficina.billing.application.port.out;

import java.time.Instant;

/** Guarda os {@code messageId} já tratados, para que a entrega repetida (at-least-once) não repita o efeito. */
public interface MensagemProcessadaRepository {
    boolean jaProcessada(String messageId);

    void registrar(String messageId, Instant quando);
}
