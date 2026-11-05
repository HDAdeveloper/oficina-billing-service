package br.com.oficina.billing.infrastructure;

import br.com.oficina.billing.application.port.out.GatewayPagamentoIndisponivelException;
import br.com.oficina.billing.application.port.out.PagamentoGateway.PagamentoConsultado;
import br.com.oficina.billing.application.port.out.PagamentoGateway.ResultadoCobranca;
import br.com.oficina.billing.application.port.out.PagamentoGateway.SolicitacaoCobranca;
import br.com.oficina.billing.infrastructure.gateway.MercadoPagoProperties;
import br.com.oficina.billing.infrastructure.gateway.PagamentoGatewayMercadoPago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Fala com um Mercado Pago de mentira: confere o que enviamos e como lemos as respostas. */
class PagamentoGatewayMercadoPagoTest {

    private static final String BASE = "https://api.mercadopago.com";
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);

    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private PagamentoGatewayMercadoPago gateway(boolean sandbox) {
        return new PagamentoGatewayMercadoPago(builder, new MercadoPagoProperties("APP_USR-teste", "seg",
                "https://exemplo.test/webhooks/mercadopago", null, 60, sandbox), RELOGIO);
    }

    private static SolicitacaoCobranca cobranca() {
        return new SolicitacaoCobranca("pag-1", "orc-1", 42L, new BigDecimal("100.00"), "LINK", "ana@x.com");
    }

    private static final String PREFERENCIA = """
            {"id":"123-abc","init_point":"https://mp.test/pagar/abc","sandbox_init_point":"https://sandbox.mp.test/pagar/abc"}
            """;

    @Test
    void criaAPreferenciaComReferenciaNotificacaoEValidade() {
        server.expect(requestTo(BASE + "/checkout/preferences"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer APP_USR-teste"))
                .andExpect(jsonPath("$.external_reference").value("pag-1"))
                .andExpect(jsonPath("$.items[0].title").value("Orçamento da OS 42"))
                .andExpect(jsonPath("$.items[0].unit_price").value(100.0))
                .andExpect(jsonPath("$.items[0].currency_id").value("BRL"))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.payer.email").value("ana@x.com"))
                .andExpect(jsonPath("$.notification_url").value("https://exemplo.test/webhooks/mercadopago"))
                .andExpect(jsonPath("$.binary_mode").value(true))
                .andExpect(jsonPath("$.expires").value(true))
                .andExpect(jsonPath("$.expiration_date_to").value("2026-10-22T10:00:00.000-03:00"))
                .andRespond(withSuccess(PREFERENCIA, MediaType.APPLICATION_JSON));

        var r = (ResultadoCobranca.LinkGerado) gateway(false).cobrar(cobranca());

        assertThat(r.idExterno()).isEqualTo("123-abc");
        assertThat(r.link()).isEqualTo("https://mp.test/pagar/abc");
        assertThat(r.expiraEm()).isEqualTo(Instant.parse("2026-10-22T13:00:00Z"));
        server.verify();
    }

    @Test
    void comSandboxUsaOLinkDeTeste() {
        server.expect(requestTo(BASE + "/checkout/preferences"))
                .andRespond(withSuccess(PREFERENCIA, MediaType.APPLICATION_JSON));

        var r = (ResultadoCobranca.LinkGerado) gateway(true).cobrar(cobranca());

        assertThat(r.link()).isEqualTo("https://sandbox.mp.test/pagar/abc");
    }

    @Test
    void respostaDeErroOuSemLinkViraProvedorIndisponivel() {
        server.expect(requestTo(BASE + "/checkout/preferences")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> gateway(false).cobrar(cobranca()))
                .isInstanceOf(GatewayPagamentoIndisponivelException.class);

        server.reset();
        server.expect(requestTo(BASE + "/checkout/preferences"))
                .andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway(false).cobrar(cobranca()))
                .isInstanceOf(GatewayPagamentoIndisponivelException.class)
                .hasMessageContaining("sem id ou link");
    }

    @Test
    void consultaTraduzOStatusDoMercadoPago() {
        String corpo = "{\"status\":\"%s\",\"status_detail\":\"x\",\"external_reference\":\"pag-1\"}";
        for (String[] par : new String[][]{{"approved", "APROVADO"}, {"rejected", "RECUSADO"},
                {"cancelled", "RECUSADO"}, {"pending", "PENDENTE"}, {"in_process", "PENDENTE"}}) {
            server.reset();
            server.expect(requestTo(BASE + "/v1/payments/777")).andExpect(method(GET))
                    .andExpect(header("Authorization", "Bearer APP_USR-teste"))
                    .andRespond(withSuccess(corpo.formatted(par[0]), MediaType.APPLICATION_JSON));

            PagamentoConsultado c = gateway(false).consultarPagamento("777");

            assertThat(c.situacao()).as(par[0]).isEqualTo(PagamentoConsultado.Situacao.valueOf(par[1]));
            assertThat(c.referencia()).isEqualTo("pag-1");
            assertThat(c.idExterno()).isEqualTo("777");
        }
    }

    @Test
    void estornoComIdNaoNumericoEhRecusadoSemChamarOProvedor() {
        assertThatThrownBy(() -> gateway(false).estornar("../x", BigDecimal.TEN))
                .isInstanceOf(br.com.oficina.billing.application.port.out.IdPagamentoExternoInvalidoException.class);
        server.verify();
    }

    @Test
    void idNaoNumericoNaoChegaAoProvedor() {
        PagamentoConsultado c = gateway(false).consultarPagamento("../../x");
        assertThat(c.referencia()).isNull();
        server.verify(); // nenhuma chamada HTTP foi esperada nem feita
    }

    @Test
    void pagamentoInexistenteNaoTemReferenciaEFalhaDeRedeEhIndisponivel() {
        server.expect(requestTo(BASE + "/v1/payments/123456")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        PagamentoConsultado c = gateway(false).consultarPagamento("123456");
        assertThat(c.referencia()).isNull();

        server.reset();
        server.expect(requestTo(BASE + "/v1/payments/777")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> gateway(false).consultarPagamento("777"))
                .isInstanceOf(GatewayPagamentoIndisponivelException.class);
    }

    @Test
    void estornoEhTotalEIdempotente() {
        server.expect(requestTo(BASE + "/v1/payments/777/refunds")).andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer APP_USR-teste"))
                .andExpect(header("X-Idempotency-Key", "estorno-777"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        gateway(false).estornar("777", new BigDecimal("100.00"));

        server.verify();
    }

    @Test
    void falhaNoEstornoViraProvedorIndisponivelParaTentarDeNovo() {
        server.expect(requestTo(BASE + "/v1/payments/777/refunds")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var g = gateway(false);
        assertThatThrownBy(() -> g.estornar("777", BigDecimal.TEN))
                .isInstanceOf(GatewayPagamentoIndisponivelException.class);
    }

    @Test
    void naoSobeSemAccessToken() {
        var semToken = new MercadoPagoProperties("", "seg", null, null, 60, false);
        assertThatThrownBy(() -> new PagamentoGatewayMercadoPago(builder, semToken, RELOGIO))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MERCADOPAGO_ACCESS_TOKEN");
    }

    @Test
    void padroesDaConfiguracao() {
        var p = new MercadoPagoProperties("t", "s", null, " ", 0, false);
        assertThat(p.baseUrl()).isEqualTo(BASE);
        assertThat(p.validadeLinkMinutos()).isEqualTo(1440);
    }
}
