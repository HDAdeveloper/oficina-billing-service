package br.com.oficina.billing.infrastructure.persistence;

import br.com.oficina.billing.domain.model.StatusPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pagamento")
public class PagamentoEntity {

    @Id
    @Column(length = 36)
    String id;

    @Version
    @Column(name = "versao", nullable = false)
    Long versao;

    @Column(name = "orcamento_id", nullable = false)
    String orcamentoId;

    @Column(name = "os_id", nullable = false)
    Long osId;

    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal valor;

    String metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    StatusPagamento status;

    @Column(name = "id_externo")
    String idExterno;

    @Column(length = 500)
    String motivo;

    @Column(name = "saga_id", length = 64)
    String sagaId;

    @Column(name = "correlation_id", length = 100)
    String correlationId;

    @Column(name = "link_pagamento", length = 500)
    String linkPagamento;

    @Column(name = "expira_em")
    Instant expiraEm;

    @Column(name = "criado_em", nullable = false)
    Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    Instant atualizadoEm;

    protected PagamentoEntity() {
    }
}
