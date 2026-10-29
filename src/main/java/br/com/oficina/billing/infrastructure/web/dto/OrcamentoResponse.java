package br.com.oficina.billing.infrastructure.web.dto;

import br.com.oficina.billing.domain.model.Orcamento;
import br.com.oficina.billing.domain.model.StatusOrcamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrcamentoResponse(String id, Long osId, String clienteId, String emailCliente, StatusOrcamento status,
                                BigDecimal valorTotal, String motivoCancelamento, List<ItemResponse> itens,
                                Instant criadoEm, Instant atualizadoEm) {

    public static OrcamentoResponse de(Orcamento o) {
        return new OrcamentoResponse(o.getId(), o.getOsId(), o.getClienteId(), o.getEmailCliente(), o.getStatus(),
                o.getValorTotal(), o.getMotivoCancelamento(),
                o.getItens().stream().map(ItemResponse::de).toList(), o.getCriadoEm(), o.getAtualizadoEm());
    }
}
