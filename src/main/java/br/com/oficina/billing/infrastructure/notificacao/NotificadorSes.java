package br.com.oficina.billing.infrastructure.notificacao;

import br.com.oficina.billing.application.port.out.NotificadorCliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Envia o link de pagamento por e-mail pelo Amazon SES. No LocalStack o mesmo código funciona (o e-mail fica
 * guardado e pode ser lido em {@code /_aws/ses}); na AWS o remetente precisa estar verificado no SES.
 */
public class NotificadorSes implements NotificadorCliente {

    private static final Logger LOG = LoggerFactory.getLogger(NotificadorSes.class);
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final SesClient ses;
    private final String remetente;

    public NotificadorSes(SesClient ses, String remetente) {
        this.ses = ses;
        this.remetente = remetente;
    }

    @Override
    public void linkDePagamento(String emailCliente, Long osId, BigDecimal valor, String link, Instant expiraEm) {
        if (emailCliente == null || emailCliente.isBlank()) {
            LOG.warn("OS {} sem e-mail do cliente: o link de pagamento não foi enviado", osId);
            return;
        }
        String texto = montarTexto(osId, valor, link, expiraEm);
        ses.sendEmail(SendEmailRequest.builder()
                .source(remetente)
                .destination(Destination.builder().toAddresses(emailCliente).build())
                .message(Message.builder()
                        .subject(Content.builder().data("Oficina Tech Service: link de pagamento da OS " + osId)
                                .charset("UTF-8").build())
                        .body(Body.builder().text(Content.builder().data(texto).charset("UTF-8").build()).build())
                        .build())
                .build());
        LOG.info("Link de pagamento da OS {} enviado por e-mail", osId);
    }

    static String montarTexto(Long osId, BigDecimal valor, String link, Instant expiraEm) {
        String dinheiro = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor);
        return "Olá!\n\n"
                + "O orçamento da sua ordem de serviço " + osId + " foi aprovado. "
                + "Para seguir com o conserto, pague " + dinheiro + " pelo link abaixo:\n\n"
                + link + "\n\n"
                + "O link vale até " + DATA_HORA.format(expiraEm) + ".\n\n"
                + "Oficina Tech Service";
    }
}
