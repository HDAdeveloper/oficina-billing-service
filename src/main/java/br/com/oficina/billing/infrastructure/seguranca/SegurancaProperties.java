package br.com.oficina.billing.infrastructure.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Prefixo {@code oficina.seguranca}. O Billing só valida tokens; quem os emite é o OS Service. */
@ConfigurationProperties(prefix = "oficina.seguranca")
public record SegurancaProperties(String jwtSecret) {
}
