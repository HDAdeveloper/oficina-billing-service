package br.com.oficina.billing.infrastructure.web;

import br.com.oficina.billing.application.port.in.ConsultaOrcamentos;
import br.com.oficina.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.domain.model.ItemOrcamento;
import br.com.oficina.billing.domain.model.Orcamento;
import br.com.oficina.billing.domain.model.Pagamento;
import br.com.oficina.billing.domain.model.TipoItem;
import br.com.oficina.billing.infrastructure.seguranca.JwtConfig;
import br.com.oficina.billing.infrastructure.seguranca.SecurityConfig;
import br.com.oficina.billing.infrastructure.web.controller.OrcamentoController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrcamentoController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class OrcamentoControllerTest {

    private static final Instant T0 = Instant.parse("2026-10-22T12:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean ConsultaOrcamentos consulta;

    private static Orcamento orcamento() {
        return Orcamento.criar("o-1", 42L, 7L, "ana@x.com",
                List.of(new ItemOrcamento(TipoItem.SERVICO, "S1", "Revisão", 2, new BigDecimal("100"))), T0);
    }

    @Test
    void semTokenDa401() throws Exception {
        mvc.perform(get("/api/orcamentos/o-1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ordens/42/orcamentos")).andExpect(status().isUnauthorized());
    }

    @Test
    void buscaOrcamentoComItensETotal() throws Exception {
        when(consulta.buscarPorId("o-1")).thenReturn(orcamento());

        mvc.perform(get("/api/orcamentos/o-1").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("o-1"))
                .andExpect(jsonPath("$.osId").value(42))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.valorTotal").value(200.0))
                .andExpect(jsonPath("$.itens[0].codigo").value("S1"))
                .andExpect(jsonPath("$.itens[0].subtotal").value(200.0));
    }

    @Test
    void orcamentoInexistenteDa404EmProblemJson() throws Exception {
        when(consulta.buscarPorId("x")).thenThrow(new RecursoNaoEncontradoException("Orçamento não encontrado: x"));

        mvc.perform(get("/api/orcamentos/x").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }

    @Test
    void listaOrcamentosDaOs() throws Exception {
        when(consulta.listarPorOs(42L)).thenReturn(List.of(orcamento()));

        mvc.perform(get("/api/ordens/42/orcamentos").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("o-1"));
    }

    @Test
    void listaPagamentosDoOrcamento() throws Exception {
        Orcamento o = orcamento();
        when(consulta.listarPagamentos("o-1")).thenReturn(List.of(Pagamento.aprovado("p-1", o, "APRO", "ext-1", T0)));

        mvc.perform(get("/api/orcamentos/o-1/pagamentos").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("p-1"))
                .andExpect(jsonPath("$[0].status").value("APROVADO"))
                .andExpect(jsonPath("$[0].valor").value(200.0));
    }
}
