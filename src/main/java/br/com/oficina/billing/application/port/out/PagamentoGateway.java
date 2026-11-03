package br.com.oficina.billing.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Porta do provedor de pagamento. A cobrança pode ser imediata (aprovada/recusada) ou por link: o provedor
 * devolve um link, o cliente paga depois e o resultado chega por webhook (ver {@link #consultarPagamento}).
 */
public interface PagamentoGateway {

    record SolicitacaoCobranca(String pagamentoId, String orcamentoId, Long osId, BigDecimal valor,
                               String metodo, String emailPagador) {
    }

    sealed interface ResultadoCobranca {
        record Aprovada(String idExterno) implements ResultadoCobranca {
        }

        record Recusada(String motivo) implements ResultadoCobranca {
        }

        /** Link criado: {@code idExterno} é o id da cobrança no provedor; o cliente ainda vai pagar. */
        record LinkGerado(String idExterno, String link, Instant expiraEm) implements ResultadoCobranca {
        }
    }

    /** Situação de um pagamento no provedor, lida por consulta (nunca confiada ao corpo do webhook). */
    record PagamentoConsultado(String idExterno, String referencia, Situacao situacao, String detalhe) {
        public enum Situacao { APROVADO, RECUSADO, PENDENTE }
    }

    ResultadoCobranca cobrar(SolicitacaoCobranca solicitacao);   // throws GatewayPagamentoIndisponivelException

    /** Consulta o pagamento no provedor. {@code referencia} é o id do nosso pagamento (external_reference). */
    PagamentoConsultado consultarPagamento(String idExterno);   // throws GatewayPagamentoIndisponivelException

    void estornar(String idExterno, BigDecimal valor);           // throws GatewayPagamentoIndisponivelException
}
