package br.com.oficina.billing.infrastructure.config;

import br.com.oficina.billing.application.port.out.NotificadorCliente;
import br.com.oficina.billing.infrastructure.notificacao.NotificadorLog;
import br.com.oficina.billing.infrastructure.notificacao.NotificadorSes;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NotificacaoConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(NotificacaoConfig.class);

    @Test
    void semConfiguracaoSoRegistraNoLog() {
        runner.run(ctx -> assertThat(ctx.getBean(NotificadorCliente.class)).isInstanceOf(NotificadorLog.class));
    }

    @Test
    void comProvedorSesUsaOSes() {
        runner.withPropertyValues("billing.email.provedor=ses", "billing.email.remetente=oficina@oficina.local",
                        "billing.email.endpoint=http://localhost:4566")
                .run(ctx -> assertThat(ctx.getBean(NotificadorCliente.class)).isInstanceOf(NotificadorSes.class));
    }
}
