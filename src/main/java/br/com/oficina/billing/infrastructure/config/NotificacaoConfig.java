package br.com.oficina.billing.infrastructure.config;

import br.com.oficina.billing.application.port.out.NotificadorCliente;
import br.com.oficina.billing.infrastructure.notificacao.NotificadorLog;
import br.com.oficina.billing.infrastructure.notificacao.NotificadorSes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

import java.net.URI;

/**
 * E-mail do link de pagamento. {@code billing.email.provedor}: {@code nenhum} (padrão: só log) ou {@code ses}.
 * Com {@code AWS_ENDPOINT_URL} preenchido (LocalStack) usa credenciais fictícias; na AWS vale a cadeia padrão.
 */
@Configuration
public class NotificacaoConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "billing.email.provedor", havingValue = "ses")
    public SesClient sesClient(@Value("${billing.email.regiao:us-east-1}") String regiao,
                               @Value("${billing.email.endpoint:}") String endpoint) {
        AvisoAwsReal.verificar(endpoint);
        var b = SesClient.builder().region(Region.of(regiao));
        if (endpoint != null && !endpoint.isBlank()) {
            b.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        }
        return b.build();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "billing.email.provedor", havingValue = "ses")
    public NotificadorCliente notificadorSes(SesClient ses,
                                             @Value("${billing.email.remetente}") String remetente) {
        return new NotificadorSes(ses, remetente);
    }

    /** Padrão: provedor ausente ou {@code nenhum}. Nunca convive com o SES (as condições são exclusivas). */
    @Bean
    @ConditionalOnProperty(name = "billing.email.provedor", havingValue = "nenhum", matchIfMissing = true)
    public NotificadorCliente notificadorLog() {
        return new NotificadorLog();
    }
}
