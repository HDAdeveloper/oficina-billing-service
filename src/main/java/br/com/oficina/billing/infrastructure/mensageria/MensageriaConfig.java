package br.com.oficina.billing.infrastructure.mensageria;

import br.com.oficina.billing.infrastructure.config.AvisoAwsReal;
import br.com.oficina.billing.application.port.in.ProcessadorComandos;
import br.com.oficina.billing.infrastructure.persistence.OutboxEventoJpa;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

import java.net.URI;
import java.time.Clock;

/** Liga o Billing às filas. Desligue com {@code billing.mensageria.habilitada=false} (testes). */
@Configuration
@EnableConfigurationProperties(MensageriaProperties.class)
@ConditionalOnProperty(name = "billing.mensageria.habilitada", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class MensageriaConfig {

    @Bean(destroyMethod = "close")
    public SqsClient sqsClient(MensageriaProperties p) {
        AvisoAwsReal.verificar(p.endpoint());
        var b = SqsClient.builder().region(Region.of(p.regiao()));
        if (p.endpoint() != null && !p.endpoint().isBlank()) {
            // LocalStack: endpoint próprio e credenciais fictícias. Na AWS vale a cadeia padrão de credenciais.
            b.endpointOverride(URI.create(p.endpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        }
        return b.build();
    }

    @Bean(destroyMethod = "close")
    public SnsClient snsClient(MensageriaProperties p) {
        var b = SnsClient.builder().region(Region.of(p.regiao()));
        if (p.endpoint() != null && !p.endpoint().isBlank()) {
            b.endpointOverride(URI.create(p.endpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        }
        return b.build();
    }

    @Bean
    public PublicadorMensagens publicadorMensagens(SnsClient sns, MensageriaProperties p) {
        return new SnsPublicadorMensagens(sns, p.topicoEventosArn());
    }

    @Bean
    public OutboxRelay outboxRelay(OutboxEventoJpa outbox, TransactionTemplate tx, ObjectMapper json,
                                   PublicadorMensagens publicador, Clock relogio, MensageriaProperties p) {
        return new OutboxRelay(outbox, tx, json, publicador, relogio, p.loteRelay());
    }

    @Bean
    public SqsComandoListener sqsComandoListener(SqsClient sqs, MensageriaProperties p, ObjectMapper json,
                                                 ProcessadorComandos processador) {
        String url = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(p.filaComandos()).build()).queueUrl();
        return new SqsComandoListener(sqs, url, new ConsumidorComandos(json, new ComandoMapper(), processador));
    }
}
