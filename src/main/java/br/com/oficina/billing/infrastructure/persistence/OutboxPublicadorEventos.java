package br.com.oficina.billing.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.oficina.billing.application.port.out.EventoSaga;
import br.com.oficina.billing.application.port.out.PublicadorEventos;
import br.com.oficina.billing.application.port.out.GeradorId;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Implementa a porta de eventos gravando na tabela outbox, dentro da transação do caso de uso.
 * O envio ao SNS é feito depois, por outro componente, lendo os registros pendentes.
 */
@Component
public class OutboxPublicadorEventos implements PublicadorEventos {

    private final OutboxEventoJpa jpa;
    private final ObjectMapper json;
    private final GeradorId ids;
    private final Clock relogio;

    public OutboxPublicadorEventos(OutboxEventoJpa jpa, ObjectMapper json, GeradorId ids, Clock relogio) {
        this.jpa = jpa;
        this.json = json;
        this.ids = ids;
        this.relogio = relogio;
    }

    @Override
    public void publicar(EventoSaga evento) {
        OutboxEventoEntity e = new OutboxEventoEntity();
        e.messageId = ids.novo();
        e.tipo = evento.type();
        e.osId = evento.osId();
        e.sagaId = evento.sagaId();
        e.correlationId = evento.correlationId();
        e.criadoEm = Instant.now(relogio);
        try {
            e.payload = json.writeValueAsString(evento.payload());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Payload do evento " + evento.type() + " inválido", ex);
        }
        jpa.save(e);
    }
}
