package br.com.oficina.billing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Evento de resposta gravado na mesma transação do estado. Um publicador (próxima fatia) lê os
 * pendentes em ordem de {@code id} e envia ao SNS.
 */
@Entity
@Table(name = "outbox_evento")
public class OutboxEventoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "message_id", nullable = false, length = 36)
    String messageId;

    @Column(nullable = false, length = 60)
    String tipo;

    @Column(name = "os_id")
    Long osId;

    @Column(name = "saga_id")
    String sagaId;

    @Column(name = "correlation_id")
    String correlationId;

    @Column(nullable = false, length = 4000)
    String payload;

    @Column(name = "criado_em", nullable = false)
    Instant criadoEm;

    @Column(name = "publicado_em")
    Instant publicadoEm;

    @Column(nullable = false)
    int tentativas;

    @Column(name = "ultimo_erro", length = 500)
    String ultimoErro;

    protected OutboxEventoEntity() {
    }

    public void marcarPublicado(Instant quando) {
        this.publicadoEm = quando;
        this.ultimoErro = null;
    }

    public void registrarFalha(String erro) {
        this.tentativas++;
        this.ultimoErro = erro == null ? null : erro.substring(0, Math.min(erro.length(), 500));
    }

    public Long getId() { return id; }
    public String getMessageId() { return messageId; }
    public String getTipo() { return tipo; }
    public Long getOsId() { return osId; }
    public String getSagaId() { return sagaId; }
    public String getCorrelationId() { return correlationId; }
    public String getPayload() { return payload; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getPublicadoEm() { return publicadoEm; }
    public int getTentativas() { return tentativas; }
    public String getUltimoErro() { return ultimoErro; }
}
