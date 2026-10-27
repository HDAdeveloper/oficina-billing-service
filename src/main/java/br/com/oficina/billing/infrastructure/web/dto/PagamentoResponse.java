package br.com.oficina.billing.infrastructure.web.dto;

import br.com.oficina.billing.domain.model.Pagamento;
import br.com.oficina.billing.domain.model.StatusPagamento;

import java.math.BigDecimal;
import java.time.Instant;

public record PagamentoResponse(String id, String orcamentoId, Long osId, BigDecimal valor, String metodo,
                                StatusPagamento status, String idExterno, String motivo, Instant criadoEm,
                                Instant atualizadoEm) {

    public static PagamentoResponse de(Pagamento p) {
        return new PagamentoResponse(p.getId(), p.getOrcamentoId(), p.getOsId(), p.getValor(), p.getMetodo(),
                p.getStatus(), p.getIdExterno(), p.getMotivo(), p.getCriadoEm(), p.getAtualizadoEm());
    }
}
