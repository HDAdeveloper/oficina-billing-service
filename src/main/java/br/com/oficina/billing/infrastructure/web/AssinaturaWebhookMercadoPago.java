package br.com.oficina.billing.infrastructure.web;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Confere que o aviso veio mesmo do Mercado Pago. O cabeçalho {@code x-signature} traz {@code ts=...,v1=...};
 * {@code v1} é o HMAC-SHA256 (hex) do texto {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} com a
 * assinatura secreta da aplicação. Partes ausentes saem do texto.
 */
public class AssinaturaWebhookMercadoPago {

    private final byte[] segredo;

    public AssinaturaWebhookMercadoPago(String segredo) {
        if (segredo == null || segredo.isBlank()) {
            throw new IllegalStateException("MERCADOPAGO_WEBHOOK_SECRET não configurado: defina a variável de ambiente"
                    + " (nunca no Git) com a assinatura secreta do webhook");
        }
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
    }

    public boolean valida(String assinatura, String requestId, String dataId) {
        if (assinatura == null || assinatura.isBlank()) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String parte : assinatura.split(",")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length == 2) {
                if (kv[0].equals("ts")) {
                    ts = kv[1];
                } else if (kv[0].equals("v1")) {
                    v1 = kv[1];
                }
            }
        }
        if (ts == null || v1 == null) {
            return false;
        }
        StringBuilder manifesto = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            manifesto.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        }
        if (requestId != null && !requestId.isBlank()) {
            manifesto.append("request-id:").append(requestId).append(';');
        }
        manifesto.append("ts:").append(ts).append(';');
        String esperado = hmac(manifesto.toString());
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /** Também usado nos testes para montar uma assinatura válida. */
    public String hmac(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível", e);
        }
    }
}
