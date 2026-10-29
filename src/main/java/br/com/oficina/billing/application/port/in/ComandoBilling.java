package br.com.oficina.billing.application.port.in;

import br.com.oficina.billing.domain.model.ItemOrcamento;

import java.math.BigDecimal;
import java.util.List;

/** Comandos que o OS Service envia ao Billing (docs/eventos.md). */
public sealed interface ComandoBilling {

    record CriarOrcamento(String clienteId, String emailCliente, List<ItemOrcamento> itens,
                          BigDecimal valorTotal) implements ComandoBilling {
    }

    record CobrarOrcamento(String orcamentoId, BigDecimal valorTotal, String metodo) implements ComandoBilling {
    }

    record CancelarOrcamento(String orcamentoId, String motivo) implements ComandoBilling {
    }

    record EstornarPagamento(String pagamentoId, String motivo) implements ComandoBilling {
    }

    /**
     * Pedido de orçamento cujos itens nem chegaram a ser válidos (quantidade zero, tipo desconhecido...).
     * Não adianta tentar de novo: o Billing responde {@code ORCAMENTO_FALHOU} com o motivo.
     */
    record OrcamentoInvalido(String motivo) implements ComandoBilling {
    }
}
