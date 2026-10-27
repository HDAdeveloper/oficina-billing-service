package br.com.oficina.billing.infrastructure.mensageria;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Prefixo {@code billing.mensageria}. {@code endpoint} só é preenchido contra o LocalStack. */
@ConfigurationProperties(prefix = "billing.mensageria")
public record MensageriaProperties(boolean habilitada, String regiao, String endpoint, String filaComandos,
                                   String topicoEventosArn, long intervaloRelayMs, int loteRelay) {
}
