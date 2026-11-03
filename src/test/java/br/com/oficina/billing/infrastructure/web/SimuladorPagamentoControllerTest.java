package br.com.oficina.billing.infrastructure.web;

import br.com.oficina.billing.application.port.in.ConfirmacaoPagamentos;
import br.com.oficina.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.infrastructure.seguranca.JwtConfig;
import br.com.oficina.billing.infrastructure.seguranca.SecurityConfig;
import br.com.oficina.billing.infrastructure.web.controller.SimuladorPagamentoController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimuladorPagamentoController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class SimuladorPagamentoControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ConfirmacaoPagamentos confirmacao;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void semTokenDa401() throws Exception {
        mvc.perform(post("/api/simulador/pagamentos/p-1/aprovar")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminAprovaEVenceOLinkSimulado() throws Exception {
        mvc.perform(post("/api/simulador/pagamentos/p-1/aprovar").with(admin())).andExpect(status().isNoContent());
        mvc.perform(post("/api/simulador/pagamentos/p-1/expirar").with(admin())).andExpect(status().isNoContent());

        verify(confirmacao).aprovarSimulado("p-1");
        verify(confirmacao).expirarAgora("p-1");
    }

    @Test
    void painelDoOsServicePodeChamarOSimuladorPeloNavegador() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .options("/api/simulador/pagamentos/p-1/aprovar")
                        .header("Origin", "http://localhost:8081")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "http://localhost:8081"));
    }

    @Test
    void outraOrigemNaoRecebeLiberacao() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .options("/api/simulador/pagamentos/p-1/aprovar")
                        .header("Origin", "http://site-qualquer.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void pagamentoInexistenteDa404() throws Exception {
        doThrow(new RecursoNaoEncontradoException("Pagamento não encontrado: x")).when(confirmacao).aprovarSimulado("x");

        mvc.perform(post("/api/simulador/pagamentos/x/aprovar").with(admin())).andExpect(status().isNotFound());
    }
}
