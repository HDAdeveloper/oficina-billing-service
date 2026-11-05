package br.com.oficina.billing.infrastructure.config;

import br.com.oficina.billing.application.port.out.PagamentoGateway;
import br.com.oficina.billing.infrastructure.gateway.MercadoPagoProperties;
import br.com.oficina.billing.infrastructure.gateway.PagamentoGatewayMercadoPago;
import br.com.oficina.billing.infrastructure.web.AssinaturaWebhookMercadoPago;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Troca o provedor simulado pelo Mercado Pago. Liga com {@code billing.pagamento.provedor=mercadopago}; o serviço
 * não sobe sem {@code MERCADOPAGO_ACCESS_TOKEN} e {@code MERCADOPAGO_WEBHOOK_SECRET}, em vez de cobrar errado.
 */
@Configuration
@ConditionalOnProperty(name = "billing.pagamento.provedor", havingValue = "mercadopago")
@EnableConfigurationProperties(MercadoPagoProperties.class)
public class MercadoPagoConfig {

    @Bean
    @org.springframework.context.annotation.Primary // o simulado só existe quando este não está ativo; evita ambiguidade
    public PagamentoGateway pagamentoGatewayMercadoPago(RestClient.Builder builder, MercadoPagoProperties p,
                                                        Clock relogio) {
        return new PagamentoGatewayMercadoPago(builder, p, relogio);
    }

    @Bean
    public AssinaturaWebhookMercadoPago assinaturaWebhookMercadoPago(MercadoPagoProperties p) {
        return new AssinaturaWebhookMercadoPago(p.webhookSecret());
    }
}
