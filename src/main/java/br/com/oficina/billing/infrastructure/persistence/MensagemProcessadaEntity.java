package br.com.oficina.billing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * A chave é o {@code messageId}, definido por nós. Implementa {@link Persistable} para o Spring Data
 * sempre INSERIR (e falhar se já existir) em vez de atualizar em silêncio.
 */
@Entity
@Table(name = "mensagem_processada")
public class MensagemProcessadaEntity implements Persistable<String> {

    @Id
    @Column(name = "message_id", length = 64)
    String messageId;

    @Column(name = "processada_em", nullable = false)
    Instant processadaEm;

    @Transient
    private boolean nova = true;

    protected MensagemProcessadaEntity() {
    }

    MensagemProcessadaEntity(String messageId, Instant processadaEm) {
        this.messageId = messageId;
        this.processadaEm = processadaEm;
    }

    @Override
    public String getId() {
        return messageId;
    }

    @Override
    public boolean isNew() {
        return nova;
    }

    @PostLoad
    @PostPersist
    void marcarComoExistente() {
        this.nova = false;
    }
}
