package br.com.oficina.billing.infrastructure.web.dto;

import br.com.oficina.billing.domain.model.ItemOrcamento;
import br.com.oficina.billing.domain.model.TipoItem;

import java.math.BigDecimal;

public record ItemResponse(TipoItem tipo, String codigo, String descricao, int quantidade,
                           BigDecimal precoUnitario, BigDecimal subtotal) {

    public static ItemResponse de(ItemOrcamento i) {
        return new ItemResponse(i.tipo(), i.codigo(), i.descricao(), i.quantidade(), i.precoUnitario(),
                i.subtotal());
    }
}
