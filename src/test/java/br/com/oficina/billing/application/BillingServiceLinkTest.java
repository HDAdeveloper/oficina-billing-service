package br.com.oficina.billing.application;

import br.com.oficina.billing.application.port.in.ComandoBilling.CancelarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.CobrarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.CriarOrcamento;
import br.com.oficina.billing.application.port.in.ContextoMensagem;
import br.com.oficina.billing.application.port.out.PagamentoGateway.ResultadoCobranca;
import br.com.oficina.billing.application.service.BillingService;
import br.com.oficina.billing.domain.model.ItemOrcamento;
import br.com.oficina.billing.domain.model.Orcamento;
import br.com.oficina.billing.domain.model.Pagamento;
import br.com.oficina.billing.domain.model.StatusOrcamento;
import br.com.oficina.billing.domain.model.StatusPagamento;
import br.com.oficina.billing.domain.model.TipoItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Cobrança por link: o Billing gera o link, guarda o pagamento como AGUARDANDO e a Saga espera. */
class BillingServiceLinkTest {

    private static final Instant T0 = Instant.parse("2026-10-22T12:00:00Z");
    private static final BigDecimal TOTAL = new BigDecimal("100.00");

    private Fakes.OrcamentosMem orcamentos;
    private Fakes.PagamentosMem pagamentos;
    private Fakes.EventosGravador eventos;
    private Fakes.GatewayFake gateway;
    private BillingService service;
    private int mensagens;

    @BeforeEach
    void setUp() {
        orcamentos = new Fakes.OrcamentosMem();
        pagamentos = new Fakes.PagamentosMem();
        eventos = new Fakes.EventosGravador();
        gateway = new Fakes.GatewayFake();
        gateway.proximoResultado = new ResultadoCobranca.LinkGerado("PREF-1", "https://mp/pagar/1", T0.plusSeconds(3600));
        service = new BillingService(orcamentos, pagamentos, new Fakes.ProcessadasMem(), eventos, gateway,
                new Fakes.IdsSequenciais(), Clock.fixed(T0, ZoneOffset.UTC));
    }

    private ContextoMensagem msg() {
        return new ContextoMensagem("msg-" + (++mensagens), "saga-1", 42L, "corr-1");
    }

    private Orcamento orcamentoCriado() {
        service.processar(msg(), new CriarOrcamento("52998224725", "ana@x.com", List.of(
                new ItemOrcamento(TipoItem.SERVICO, "S1", "Revisão", 1, TOTAL)), TOTAL));
        return orcamentos.dados.values().iterator().next();
    }

    private void cobrar(Orcamento o) {
        service.processar(msg(), new CobrarOrcamento(o.getId(), TOTAL, "LINK"));
    }

    @Test
    void cobrancaPorLinkGuardaPagamentoAguardandoEPublicaOLink() {
        Orcamento o = orcamentoCriado();
        eventos.publicados.clear();

        cobrar(o);

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PENDENTE);
        Pagamento p = pagamentos.dados.values().iterator().next();
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.AGUARDANDO);
        assertThat(p.getLinkPagamento()).isEqualTo("https://mp/pagar/1");
        assertThat(p.getSagaId()).isEqualTo("saga-1");
        assertThat(p.getCorrelationId()).isEqualTo("corr-1");
        assertThat(eventos.tipos()).containsExactly("LINK_PAGAMENTO_GERADO");
        assertThat(eventos.ultimo().payload())
                .containsEntry("pagamentoId", p.getId())
                .containsEntry("orcamentoId", o.getId())
                .containsEntry("link", "https://mp/pagar/1")
                .containsEntry("expiraEm", T0.plusSeconds(3600).toString());
    }

    @Test
    void cobrancaRepetidaDevolveOMesmoLinkSemCriarOutro() {
        Orcamento o = orcamentoCriado();

        cobrar(o);
        cobrar(o);

        assertThat(gateway.cobrancas).isEqualTo(1);
        assertThat(pagamentos.dados).hasSize(1);
        assertThat(eventos.tipos()).endsWith("LINK_PAGAMENTO_GERADO", "LINK_PAGAMENTO_GERADO");
        assertThat(eventos.ultimo().payload()).containsEntry("link", "https://mp/pagar/1");
    }

    @Test
    void linkVencidoEhSubstituidoPorUmNovoNaProximaCobranca() {
        Orcamento o = orcamentoCriado();
        gateway.proximoResultado = new ResultadoCobranca.LinkGerado("PREF-VELHA", "https://mp/pagar/velha", T0.minusSeconds(60));
        cobrar(o);
        gateway.proximoResultado = new ResultadoCobranca.LinkGerado("PREF-NOVA", "https://mp/pagar/nova", T0.plusSeconds(3600));

        cobrar(o);

        assertThat(gateway.cobrancas).isEqualTo(2);
        assertThat(pagamentos.dados.values()).extracting(Pagamento::getStatus)
                .containsExactlyInAnyOrder(StatusPagamento.EXPIRADO, StatusPagamento.AGUARDANDO);
        assertThat(eventos.ultimo().payload()).containsEntry("link", "https://mp/pagar/nova");
    }

    @Test
    void cancelarOOrcamentoTiraOLinkDeCirculacao() {
        Orcamento o = orcamentoCriado();
        cobrar(o);

        service.processar(msg(), new CancelarOrcamento(o.getId(), "Pagamento não realizado"));

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(pagamentos.dados.values()).singleElement().satisfies(p -> {
            assertThat(p.getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
            assertThat(p.getMotivo()).contains("Pagamento não realizado");
        });
        assertThat(eventos.ultimo().type()).isEqualTo("ORCAMENTO_CANCELADO");
    }
}
