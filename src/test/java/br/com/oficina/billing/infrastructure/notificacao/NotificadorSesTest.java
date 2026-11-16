package br.com.oficina.billing.infrastructure.notificacao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotificadorSesTest {

    private static final Instant EXPIRA = Instant.parse("2026-11-17T15:00:00Z");

    @Test
    void enviaEmailComOLinkParaOCliente() {
        SesClient ses = mock(SesClient.class);
        var notificador = new NotificadorSes(ses, "oficina@oficina.local");

        notificador.linkDePagamento("ana@x.com", 42L, new BigDecimal("150.00"), "https://pagar/abc", EXPIRA);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).sendEmail(captor.capture());
        SendEmailRequest req = captor.getValue();
        assertThat(req.source()).isEqualTo("oficina@oficina.local");
        assertThat(req.destination().toAddresses()).containsExactly("ana@x.com");
        assertThat(req.message().subject().data()).contains("OS 42");
        assertThat(req.message().body().text().data())
                .contains("https://pagar/abc").contains("ordem de serviço 42").contains("150,00")
                .contains("17/11/2026 12:00");
    }

    @Test
    void semEmailDoClienteNaoEnvia() {
        SesClient ses = mock(SesClient.class);
        var notificador = new NotificadorSes(ses, "oficina@oficina.local");

        notificador.linkDePagamento(" ", 42L, BigDecimal.TEN, "https://pagar/abc", EXPIRA);
        notificador.linkDePagamento(null, 42L, BigDecimal.TEN, "https://pagar/abc", EXPIRA);

        verify(ses, never()).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void notificadorDeLogNaoFalha() {
        assertThatCode(() -> new NotificadorLog()
                .linkDePagamento("ana@x.com", 42L, BigDecimal.TEN, "https://pagar/abc", EXPIRA)).doesNotThrowAnyException();
    }
}
