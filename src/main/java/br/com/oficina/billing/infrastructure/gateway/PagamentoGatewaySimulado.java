package br.com.oficina.billing.infrastructure.gateway;

import br.com.oficina.billing.application.port.out.GatewayPagamentoIndisponivelException;
import br.com.oficina.billing.application.port.out.PagamentoGateway;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * Provedor de pagamento simulado, para desenvolver e demonstrar sem conta no Mercado Pago.
 * O campo {@code metodo} do pedido de cobrança comanda o resultado, no estilo dos cartões de teste
 * do Mercado Pago:
 * <ul>
 *   <li>vazio, {@code APRO} ou qualquer outro valor (PIX, CARTAO...): aprovado;</li>
 *   <li>{@code FUND}: recusado por saldo insuficiente;</li>
 *   <li>{@code OTHE}: recusado pelo emissor;</li>
 *   <li>{@code ERRO}: provedor indisponível (a mensagem é tentada de novo e vai à DLQ);</li>
 *   <li>{@code ERRO_ESTORNO}: a cobrança é aprovada, mas o estorno depois falha (indisponível).</li>
 * </ul>
 */
public class PagamentoGatewaySimulado implements PagamentoGateway {

    @Override
    public ResultadoCobranca cobrar(SolicitacaoCobranca s) {
        String metodo = normalizar(s.metodo());
        return switch (metodo) {
            case "FUND" -> new ResultadoCobranca.Recusada("Saldo insuficiente");
            case "OTHE" -> new ResultadoCobranca.Recusada("Pagamento recusado pelo emissor");
            case "ERRO" -> throw new GatewayPagamentoIndisponivelException("Provedor de pagamento indisponível (simulado)");
            default -> new ResultadoCobranca.Aprovada("SIM-" + metodo + "-" + UUID.randomUUID());
        };
    }

    @Override
    public void estornar(String idExterno, BigDecimal valor) {
        if (idExterno != null && idExterno.contains("ERRO_ESTORNO")) {
            throw new GatewayPagamentoIndisponivelException("Provedor de pagamento indisponível no estorno (simulado)");
        }
    }

    private static String normalizar(String metodo) {
        return metodo == null || metodo.isBlank() ? "APRO" : metodo.trim().toUpperCase(Locale.ROOT);
    }
}
