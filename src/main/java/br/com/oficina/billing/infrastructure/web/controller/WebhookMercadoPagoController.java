package br.com.oficina.billing.infrastructure.web.controller;

import br.com.oficina.billing.application.port.in.ConfirmacaoPagamentos;
import br.com.oficina.billing.application.port.out.GatewayPagamentoIndisponivelException;
import br.com.oficina.billing.infrastructure.web.AssinaturaWebhookMercadoPago;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recebe os avisos do Mercado Pago. Rota pública (o Mercado Pago não tem nosso JWT), protegida pela assinatura.
 * O aviso só diz "o pagamento X mudou": o estado verdadeiro é consultado no provedor. Responde 200 sempre que o
 * aviso foi entendido (mesmo sem nada a fazer); 503 quando o provedor falhou, para o Mercado Pago reenviar.
 */
@RestController
@ConditionalOnProperty(name = "billing.pagamento.provedor", havingValue = "mercadopago")
@RequestMapping("/webhooks/mercadopago")
@Hidden
public class WebhookMercadoPagoController {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookMercadoPagoController.class);
    private static final java.util.regex.Pattern ID_PAGAMENTO = java.util.regex.Pattern.compile("\\d{1,20}");

    private final ConfirmacaoPagamentos confirmacao;
    private final AssinaturaWebhookMercadoPago assinatura;

    public WebhookMercadoPagoController(ConfirmacaoPagamentos confirmacao, AssinaturaWebhookMercadoPago assinatura) {
        this.confirmacao = confirmacao;
        this.assinatura = assinatura;
    }

    @PostMapping
    public ResponseEntity<Void> receber(
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam(value = "data.id", required = false) String dataIdQuery,
            @RequestParam(value = "type", required = false) String tipoQuery,
            @RequestBody(required = false) JsonNode corpo) {

        String dataId = dataIdQuery != null ? dataIdQuery : texto(corpo == null ? null : corpo.get("data"), "id");
        if (!assinatura.valida(xSignature, xRequestId, dataId)) {
            LOG.warn("Webhook do Mercado Pago com assinatura inválida; rejeitado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String tipo = tipoQuery != null ? tipoQuery : texto(corpo, "type");
        // O id do pagamento no Mercado Pago é numérico; qualquer outra coisa não é um aviso real (e não vai para URL nem log)
        if (!"payment".equals(tipo) || dataId == null || !ID_PAGAMENTO.matcher(dataId).matches()) {
            LOG.info("Webhook do Mercado Pago ignorado (não é um aviso de pagamento válido)");
            return ResponseEntity.ok().build();
        }
        try {
            confirmacao.pagamentoAtualizado(dataId);
            return ResponseEntity.ok().build();
        } catch (GatewayPagamentoIndisponivelException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode n = no == null ? null : no.get(campo);
        return n == null || n.isNull() ? null : n.asText();
    }
}
