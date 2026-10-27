package br.com.oficina.billing.infrastructure.persistence;

import br.com.oficina.billing.domain.model.TipoItem;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

@Embeddable
class ItemEmb {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    TipoItem tipo;

    @Column(nullable = false)
    String codigo;

    @Column(nullable = false)
    String descricao;

    @Column(nullable = false)
    int quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
    BigDecimal precoUnitario;

    protected ItemEmb() {
    }

    ItemEmb(TipoItem tipo, String codigo, String descricao, int quantidade, BigDecimal precoUnitario) {
        this.tipo = tipo;
        this.codigo = codigo;
        this.descricao = descricao;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
    }
}
