package br.com.oficina.billing.infrastructure.mensageria;

import br.com.oficina.billing.application.port.in.ConfirmacaoPagamentos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Rotina periódica que vence os links de pagamento não pagos e avisa a Saga (compensação A). */
public class ExpiradorDeLinks {

    private static final Logger LOG = LoggerFactory.getLogger(ExpiradorDeLinks.class);

    private final ConfirmacaoPagamentos confirmacao;

    public ExpiradorDeLinks(ConfirmacaoPagamentos confirmacao) {
        this.confirmacao = confirmacao;
    }

    @Scheduled(fixedDelayString = "${billing.pagamento.intervalo-expiracao-ms:10000}")
    public void executar() {
        try {
            int vencidos = confirmacao.expirarVencidos();
            if (vencidos > 0) {
                LOG.info("{} link(s) de pagamento vencido(s) sem pagamento", vencidos);
            }
        } catch (RuntimeException e) {
            LOG.warn("Falha ao vencer links de pagamento; nova tentativa na próxima rodada", e);
        }
    }
}
