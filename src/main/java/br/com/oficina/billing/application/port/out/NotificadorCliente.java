package br.com.oficina.billing.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Avisa o cliente. Hoje só o link de pagamento: é por ele que o cliente paga, então precisa chegar a ele.
 * A implementação é trocável (SES, log); falha no envio nunca derruba a Saga.
 */
public interface NotificadorCliente {

    void linkDePagamento(String emailCliente, Long osId, BigDecimal valor, String link, Instant expiraEm);
}
