package br.com.oficina.billing.infrastructure.gateway;

import br.com.oficina.billing.application.port.out.GatewayPagamentoIndisponivelException;
import br.com.oficina.billing.application.port.out.PagamentoGateway;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Provedor de pagamento simulado, para desenvolver e demonstrar sem conta no Mercado Pago.
 * O campo {@code metodo} do pedido de cobrança comanda o resultado, no estilo dos cartões de teste
 * do Mercado Pago:
 * <ul>
 *   <li>vazio, {@code APRO} ou qualquer outro valor (PIX, CARTAO...): aprovado na hora;</li>
 *   <li>{@code LINK}: gera um link e espera; quem "paga" é o endpoint de simulação
 *       ({@code POST /api/simulador/pagamentos/{id}/aprovar}) ou o vencimento do link;</li>
 *   <li>{@code FUND}: recusado por saldo insuficiente;</li>
 *   <li>{@code OTHE}: recusado pelo emissor;</li>
 *   <li>{@code ERRO}: provedor indisponível (a mensagem é tentada de novo e vai à DLQ);</li>
 *   <li>{@code ERRO_ESTORNO}: a cobrança é aprovada, mas o estorno depois falha (indisponível).</li>
 * </ul>
 */
public class PagamentoGatewaySimulado implements PagamentoGateway {

    private final Clock relogio;
    private final Duration validadeLink;
    private final String urlBaseLink;

    public PagamentoGatewaySimulado() {
        this(Clock.systemUTC(), Duration.ofHours(24), "http://localhost:8082/pagamento-simulado");
    }

    public PagamentoGatewaySimulado(Clock relogio, Duration validadeLink, String urlBaseLink) {
        this.relogio = relogio;
        this.validadeLink = validadeLink;
        this.urlBaseLink = urlBaseLink;
    }

    @Override
    public ResultadoCobranca cobrar(SolicitacaoCobranca s) {
        String metodo = normalizar(s.metodo());
        return switch (metodo) {
            case "FUND" -> new ResultadoCobranca.Recusada("Saldo insuficiente");
            case "OTHE" -> new ResultadoCobranca.Recusada("Pagamento recusado pelo emissor");
            case "ERRO" -> throw new GatewayPagamentoIndisponivelException("Provedor de pagamento indisponível (simulado)");
            case "LINK" -> new ResultadoCobranca.LinkGerado("SIM-PREF-" + s.pagamentoId(),
                    urlBaseLink + "/" + s.pagamentoId(), Instant.now(relogio).plus(validadeLink));
            default -> new ResultadoCobranca.Aprovada("SIM-" + metodo + "-" + UUID.randomUUID());
        };
    }

    @Override
    public PagamentoConsultado consultarPagamento(String idExterno) {
        // No simulador não há webhook: a aprovação entra pelo endpoint de simulação
        throw new GatewayPagamentoIndisponivelException("O provedor simulado não tem consulta de pagamento");
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
