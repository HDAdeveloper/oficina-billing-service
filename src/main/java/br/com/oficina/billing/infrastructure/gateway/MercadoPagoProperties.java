package br.com.oficina.billing.infrastructure.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Prefixo {@code billing.mercadopago}. Só vale com {@code billing.pagamento.provedor=mercadopago}.
 * {@code accessToken} e {@code webhookSecret} NUNCA vão para o Git: vêm de variável de ambiente ou Secret.
 *
 * @param accessToken       Access Token da aplicação (teste: começa com APP_USR); autentica as chamadas à API
 * @param webhookSecret     "assinatura secreta" do webhook, mostrada no painel da aplicação
 * @param notificationUrl   URL pública (HTTPS) que o Mercado Pago chama; localhost não funciona
 * @param validadeLinkMinutos quanto tempo o link vale
 * @param usarSandboxLink   usa o sandbox_init_point em vez do init_point
 */
@ConfigurationProperties(prefix = "billing.mercadopago")
public record MercadoPagoProperties(String accessToken, String webhookSecret, String notificationUrl,
                                    String baseUrl, long validadeLinkMinutos, boolean usarSandboxLink) {

    public MercadoPagoProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.mercadopago.com" : baseUrl;
        validadeLinkMinutos = validadeLinkMinutos <= 0 ? 24 * 60 : validadeLinkMinutos;
    }
}
