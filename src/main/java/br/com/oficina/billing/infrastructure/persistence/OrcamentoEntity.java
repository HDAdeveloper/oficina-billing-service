package br.com.oficina.billing.infrastructure.persistence;

import br.com.oficina.billing.domain.model.StatusOrcamento;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Modelo de persistência: fica só na infraestrutura, o domínio não conhece JPA. */
@Entity
@Table(name = "orcamento")
public class OrcamentoEntity {

    @Id
    @Column(length = 36)
    String id;

    @Version
    @Column(name = "versao", nullable = false)
    Long versao;

    @Column(name = "os_id", nullable = false)
    Long osId;

    @Column(name = "cliente_id")
    String clienteId;

    @Column(name = "email_cliente")
    String emailCliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    StatusOrcamento status;

    @Column(name = "motivo_cancelamento", length = 500)
    String motivoCancelamento;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    BigDecimal valorTotal;

    @Column(name = "criado_em", nullable = false)
    Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    Instant atualizadoEm;

    @ElementCollection
    @CollectionTable(name = "orcamento_item", joinColumns = @JoinColumn(name = "orcamento_id"))
    @OrderColumn(name = "ordem")
    List<ItemEmb> itens = new ArrayList<>();

    protected OrcamentoEntity() {
    }
}
