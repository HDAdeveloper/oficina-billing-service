package br.com.oficina.billing.application.port.out;

import java.math.BigDecimal;

/**
 * Porta para o provedor de pagamento. Hoje há um adaptador simulado; o do Mercado Pago entra atrás
 * desta mesma porta, sem mudar o domínio.
 */
public interface PagamentoGateway {

    record SolicitacaoCobranca(String pagamentoId, String orcamentoId, Long osId, BigDecimal valor,
                               String metodo, String emailPagador) {
    }

    sealed interface ResultadoCobranca {
        /** Provedor aprovou; {@code idExterno} identifica a transação lá. */
        record Aprovada(String idExterno) implements ResultadoCobranca {
        }

        /** Provedor recusou (saldo, cartão inválido etc.): é uma resposta de negócio, não uma falha. */
        record Recusada(String motivo) implements ResultadoCobranca {
        }
    }

    /** @throws GatewayPagamentoIndisponivelException se o provedor não responder */
    ResultadoCobranca cobrar(SolicitacaoCobranca solicitacao);

    /** @throws GatewayPagamentoIndisponivelException se o provedor não responder */
    void estornar(String idExterno, BigDecimal valor);
}
