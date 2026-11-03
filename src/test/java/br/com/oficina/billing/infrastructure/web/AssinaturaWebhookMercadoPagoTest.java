package br.com.oficina.billing.infrastructure.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssinaturaWebhookMercadoPagoTest {

    private final AssinaturaWebhookMercadoPago assinatura = new AssinaturaWebhookMercadoPago("segredo-de-teste");

    private String assinar(String manifesto, String ts) {
        return "ts=" + ts + ",v1=" + assinatura.hmac(manifesto);
    }

    @Test
    void aceitaAssinaturaCalculadaComOManifestoCompleto() {
        String header = assinar("id:12345;request-id:req-1;ts:1700000000;", "1700000000");
        assertThat(assinatura.valida(header, "req-1", "12345")).isTrue();
    }

    @Test
    void idComLetrasEntraMinusculoNoManifesto() {
        String header = assinar("id:abc123;request-id:req-1;ts:1700000000;", "1700000000");
        assertThat(assinatura.valida(header, "req-1", "ABC123")).isTrue();
    }

    @Test
    void partesAusentesSaemDoManifesto() {
        String header = assinar("ts:1700000000;", "1700000000");
        assertThat(assinatura.valida(header, null, null)).isTrue();
    }

    @Test
    void recusaOutroSegredoOutroIdOuOutroRequestId() {
        String header = assinar("id:12345;request-id:req-1;ts:1700000000;", "1700000000");
        assertThat(new AssinaturaWebhookMercadoPago("outro").valida(header, "req-1", "12345")).isFalse();
        assertThat(assinatura.valida(header, "req-1", "99999")).isFalse();
        assertThat(assinatura.valida(header, "req-2", "12345")).isFalse();
    }

    @Test
    void recusaCabecalhoAusenteMalFormadoOuIncompleto() {
        assertThat(assinatura.valida(null, "r", "1")).isFalse();
        assertThat(assinatura.valida("  ", "r", "1")).isFalse();
        assertThat(assinatura.valida("lixo", "r", "1")).isFalse();
        assertThat(assinatura.valida("ts=1", "r", "1")).isFalse();
        assertThat(assinatura.valida("v1=abc", "r", "1")).isFalse();
    }

    @Test
    void naoSobeSemSegredo() {
        assertThatThrownBy(() -> new AssinaturaWebhookMercadoPago(" ")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AssinaturaWebhookMercadoPago(null)).isInstanceOf(IllegalStateException.class);
    }
}
