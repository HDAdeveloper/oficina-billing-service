package br.com.oficina.billing.infrastructure.gateway;

import br.com.oficina.billing.application.port.out.GatewayPagamentoIndisponivelException;
import br.com.oficina.billing.application.port.out.PagamentoGateway;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mercado Pago, Checkout Pro: cada cobrança vira uma "preferência" e o cliente recebe o link para pagar na página
 * do Mercado Pago (nenhum dado de cartão passa por aqui). O resultado chega depois, por webhook, e é sempre
 * confirmado por consulta ({@link #consultarPagamento}).
 */
public class PagamentoGatewayMercadoPago implements PagamentoGateway {

    private static final Logger LOG = LoggerFactory.getLogger(PagamentoGatewayMercadoPago.class);
    private static final DateTimeFormatter FORMATO_VALIDADE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final RestClient http;
    private final MercadoPagoProperties config;
    private final Clock relogio;

    public PagamentoGatewayMercadoPago(RestClient.Builder builder, MercadoPagoProperties config, Clock relogio) {
        if (config.accessToken() == null || config.accessToken().isBlank()) {
            throw new IllegalStateException("MERCADOPAGO_ACCESS_TOKEN não configurado: defina a variável de ambiente"
                    + " (nunca no Git) ou use billing.pagamento.provedor=simulado");
        }
        this.http = builder.baseUrl(config.baseUrl())
                .defaultHeader("Authorization", "Bearer " + config.accessToken())
                .build();
        this.config = config;
        this.relogio = relogio;
    }

    @Override
    public ResultadoCobranca cobrar(SolicitacaoCobranca s) {
        Instant expiraEm = Instant.now(relogio).plus(Duration.ofMinutes(config.validadeLinkMinutos()));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", s.orcamentoId());
        item.put("title", "Orçamento da OS " + s.osId());
        item.put("quantity", 1);
        item.put("currency_id", "BRL");
        item.put("unit_price", s.valor());

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("items", List.of(item));
        corpo.put("external_reference", s.pagamentoId());
        if (s.emailPagador() != null && !s.emailPagador().isBlank()) {
            corpo.put("payer", Map.of("email", s.emailPagador()));
        }
        if (config.notificationUrl() != null && !config.notificationUrl().isBlank()) {
            corpo.put("notification_url", config.notificationUrl());
        }
        corpo.put("binary_mode", true); // aprovado ou recusado, sem "pendente"
        corpo.put("expires", true);
        corpo.put("expiration_date_to", expiraEm.atZone(FUSO).format(FORMATO_VALIDADE));

        JsonNode resposta = executar("criar a preferência de pagamento", () -> http.post()
                .uri("/checkout/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(JsonNode.class));

        String id = texto(resposta, "id");
        String link = config.usarSandboxLink() ? texto(resposta, "sandbox_init_point") : texto(resposta, "init_point");
        if (id == null || link == null) {
            throw new GatewayPagamentoIndisponivelException("Resposta do Mercado Pago sem id ou link de pagamento");
        }
        return new ResultadoCobranca.LinkGerado(id, link, expiraEm);
    }

    @Override
    public PagamentoConsultado consultarPagamento(String idExterno) {
        JsonNode p;
        try {
            p = executar("consultar o pagamento " + idExterno, () -> http.get()
                    .uri("/v1/payments/{id}", idExterno)
                    .retrieve()
                    .body(JsonNode.class));
        } catch (PagamentoInexistente e) {
            // Aviso de teste do painel ("simular notificação") ou id de outra conta: nada a fazer
            return new PagamentoConsultado(idExterno, null, PagamentoConsultado.Situacao.PENDENTE, "inexistente");
        }
        String status = texto(p, "status");
        PagamentoConsultado.Situacao situacao = switch (status == null ? "" : status) {
            case "approved" -> PagamentoConsultado.Situacao.APROVADO;
            case "rejected", "cancelled" -> PagamentoConsultado.Situacao.RECUSADO;
            default -> PagamentoConsultado.Situacao.PENDENTE; // pending, in_process, refunded...
        };
        return new PagamentoConsultado(idExterno, texto(p, "external_reference"), situacao, texto(p, "status_detail"));
    }

    @Override
    public void estornar(String idExterno, BigDecimal valor) {
        executar("estornar o pagamento " + idExterno, () -> http.post()
                .uri("/v1/payments/{id}/refunds", idExterno)
                .header("X-Idempotency-Key", "estorno-" + idExterno) // repetir o estorno não devolve duas vezes
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of()) // corpo vazio = estorno total
                .retrieve()
                .toBodilessEntity());
        LOG.info("Pagamento {} estornado no Mercado Pago", idExterno);
    }

    // ------------------------------------------------------------ apoio

    private interface Chamada<T> {
        T executar();
    }

    private static class PagamentoInexistente extends RuntimeException {
        PagamentoInexistente() {
            super(null, null, false, false);
        }
    }

    /** Falha de rede ou resposta de erro viram "provedor indisponível": a mensagem é tentada de novo. */
    private <T> T executar(String acao, Chamada<T> chamada) {
        try {
            return chamada.executar();
        } catch (HttpClientErrorException.NotFound e) {
            if (acao.startsWith("consultar")) {
                throw new PagamentoInexistente();
            }
            throw indisponivel(acao, e);
        } catch (RestClientException e) {
            throw indisponivel(acao, e);
        }
    }

    private static GatewayPagamentoIndisponivelException indisponivel(String acao, RestClientException e) {
        // Não registra o corpo da requisição nem o token; só a ação e o motivo
        LOG.warn("Mercado Pago: falha ao {}: {}", acao, e.getMessage());
        return new GatewayPagamentoIndisponivelException("Mercado Pago indisponível ao " + acao, e);
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode n = no == null ? null : no.get(campo);
        return n == null || n.isNull() || n.asText().isBlank() ? null : n.asText();
    }
}
