package br.com.oficina.billing.infrastructure.notificacao;

import br.com.oficina.billing.application.port.out.NotificadorCliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;

/** Padrão quando o e-mail não está ligado: só registra no log (o link continua disponível na API). */
public class NotificadorLog implements NotificadorCliente {

    private static final Logger LOG = LoggerFactory.getLogger(NotificadorLog.class);

    @Override
    public void linkDePagamento(String emailCliente, Long osId, BigDecimal valor, String link, Instant expiraEm) {
        LOG.info("E-mail desligado (billing.email.provedor=nenhum). Link da OS {} para {}: {}", osId, emailCliente, link);
    }
}
