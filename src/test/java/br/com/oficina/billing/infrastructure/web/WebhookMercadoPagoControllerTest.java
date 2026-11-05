package br.com.oficina.billing.infrastructure.web;

import br.com.oficina.billing.application.port.in.ConfirmacaoPagamentos;
import br.com.oficina.billing.application.port.out.GatewayPagamentoIndisponivelException;
import br.com.oficina.billing.infrastructure.seguranca.JwtConfig;
import br.com.oficina.billing.infrastructure.seguranca.SecurityConfig;
import br.com.oficina.billing.infrastructure.web.controller.WebhookMercadoPagoController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WebhookMercadoPagoController.class, properties = "billing.pagamento.provedor=mercadopago")
@Import({SecurityConfig.class, JwtConfig.class})
class WebhookMercadoPagoControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ConfirmacaoPagamentos confirmacao;
    @MockitoBean AssinaturaWebhookMercadoPago assinatura;

    private static final String CORPO = "{\"type\":\"payment\",\"action\":\"payment.updated\",\"data\":{\"id\":\"123\"}}";

    @Test
    void rotaEhPublicaMasExigeAssinaturaValida() throws Exception {
        when(assinatura.valida("ts=1,v1=x", "req-1", "123")).thenReturn(false);

        mvc.perform(post("/webhooks/mercadopago?data.id=123&type=payment")
                        .header("x-signature", "ts=1,v1=x").header("x-request-id", "req-1")
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized());

        verify(confirmacao, never()).pagamentoAtualizado(anyString());
    }

    @Test
    void avisoDePagamentoComAssinaturaValidaConfirmaOPagamento() throws Exception {
        when(assinatura.valida("ts=1,v1=ok", "req-1", "123")).thenReturn(true);

        mvc.perform(post("/webhooks/mercadopago?data.id=123&type=payment")
                        .header("x-signature", "ts=1,v1=ok").header("x-request-id", "req-1")
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk());

        verify(confirmacao).pagamentoAtualizado("123");
    }

    @Test
    void idPodeVirNoCorpoQuandoNaoVemNaQuery() throws Exception {
        when(assinatura.valida("ts=1,v1=ok", "req-1", "123")).thenReturn(true);

        mvc.perform(post("/webhooks/mercadopago")
                        .header("x-signature", "ts=1,v1=ok").header("x-request-id", "req-1")
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk());

        verify(confirmacao).pagamentoAtualizado("123");
    }

    @Test
    void idQueNaoEhNumericoEIgnoradoSemConsultarOProvedor() throws Exception {
        when(assinatura.valida("ts=1,v1=ok", "req-1", "../x")).thenReturn(true);

        mvc.perform(post("/webhooks/mercadopago?data.id=../x&type=payment")
                        .header("x-signature", "ts=1,v1=ok").header("x-request-id", "req-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        verify(confirmacao, never()).pagamentoAtualizado(anyString());
    }

    @Test
    void outrosTiposDeAvisoSaoReconhecidosSemFazerNada() throws Exception {
        when(assinatura.valida("ts=1,v1=ok", "req-1", "55")).thenReturn(true);

        mvc.perform(post("/webhooks/mercadopago?data.id=55&type=merchant_order")
                        .header("x-signature", "ts=1,v1=ok").header("x-request-id", "req-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        verify(confirmacao, never()).pagamentoAtualizado(anyString());
    }

    @Test
    void falhaDoProvedorDa503ParaOMercadoPagoReenviar() throws Exception {
        when(assinatura.valida("ts=1,v1=ok", "req-1", "123")).thenReturn(true);
        doThrow(new GatewayPagamentoIndisponivelException("fora")).when(confirmacao).pagamentoAtualizado("123");

        mvc.perform(post("/webhooks/mercadopago?data.id=123&type=payment")
                        .header("x-signature", "ts=1,v1=ok").header("x-request-id", "req-1")
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isServiceUnavailable());
    }
}
