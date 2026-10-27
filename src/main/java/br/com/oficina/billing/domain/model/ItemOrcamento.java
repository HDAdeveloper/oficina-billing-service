package br.com.oficina.billing.domain.model;

import br.com.oficina.billing.domain.exception.RegraDeNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Linha do orçamento. Os preços chegam prontos do OS Service: o Billing não tem catálogo. */
public record ItemOrcamento(TipoItem tipo, String codigo, String descricao, int quantidade,
                            BigDecimal precoUnitario) {

    public ItemOrcamento {
        if (tipo == null) {
            throw new RegraDeNegocioException("Tipo do item é obrigatório");
        }
        if (codigo == null || codigo.isBlank()) {
            throw new RegraDeNegocioException("Código do item é obrigatório");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new RegraDeNegocioException("Descrição do item é obrigatória");
        }
        if (quantidade <= 0) {
            throw new RegraDeNegocioException("Quantidade do item deve ser maior que zero");
        }
        if (precoUnitario == null || precoUnitario.signum() < 0) {
            throw new RegraDeNegocioException("Preço unitário não pode ser negativo");
        }
        codigo = codigo.trim();
        descricao = descricao.trim();
        // Escala fixa em 2 casas: igualdade e soma não dependem de como o valor chegou (10 x 10.00)
        precoUnitario = precoUnitario.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal subtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }
}
