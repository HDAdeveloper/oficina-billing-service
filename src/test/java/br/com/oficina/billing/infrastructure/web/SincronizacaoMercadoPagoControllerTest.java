package br.com.oficina.billing.infrastructure.web;

import br.com.oficina.billing.application.port.in.ConfirmacaoPagamentos;
import br.com.oficina.billing.infrastructure.seguranca.JwtConfig;
import br.com.oficina.billing.infrastructure.seguranca.SecurityConfig;
import br.com.oficina.billing.infrastructure.web.controller.SincronizacaoMercadoPagoController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SincronizacaoMercadoPagoController.class, properties = "billing.pagamento.provedor=mercadopago")
@Import({SecurityConfig.class, JwtConfig.class})
class SincronizacaoMercadoPagoControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ConfirmacaoPagamentos confirmacao;

    @Test
    void semTokenDa401() throws Exception {
        mvc.perform(post("/api/mercadopago/pagamentos/123/sincronizar")).andExpect(status().isUnauthorized());
        verify(confirmacao, never()).pagamentoAtualizado(anyString());
    }

    @Test
    void clienteNaoPodeConciliar() throws Exception {
        mvc.perform(post("/api/mercadopago/pagamentos/123/sincronizar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
        verify(confirmacao, never()).pagamentoAtualizado(anyString());
    }

    @Test
    void adminConciliaUmPagamentoDoMercadoPago() throws Exception {
        mvc.perform(post("/api/mercadopago/pagamentos/123/sincronizar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
        verify(confirmacao).pagamentoAtualizado("123");
    }
}
