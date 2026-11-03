package br.com.oficina.billing.domain;

import br.com.oficina.billing.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.domain.model.ItemOrcamento;
import br.com.oficina.billing.domain.model.Orcamento;
import br.com.oficina.billing.domain.model.Pagamento;
import br.com.oficina.billing.domain.model.StatusPagamento;
import br.com.oficina.billing.domain.model.TipoItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagamentoLinkTest {

    private static final Instant T0 = Instant.parse("2026-10-22T12:00:00Z");

    private static Orcamento orcamento() {
        return Orcamento.criar("orc-1", 42L, "52998224725", "ana@x.com",
                List.of(new ItemOrcamento(TipoItem.SERVICO, "S1", "Revisão", 1, new BigDecimal("100.00"))), T0);
    }

    private static Pagamento aguardando(Instant expiraEm) {
        return Pagamento.aguardando("pag-1", orcamento(), "LINK", "PREF-1", "https://mp/pagar/1", expiraEm,
                "saga-1", "corr-1", T0);
    }

    @Test
    void nasceAguardandoComLinkSagaECorrelacao() {
        Pagamento p = aguardando(T0.plusSeconds(3600));

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.AGUARDANDO);
        assertThat(p.getLinkPagamento()).isEqualTo("https://mp/pagar/1");
        assertThat(p.getSagaId()).isEqualTo("saga-1");
        assertThat(p.getCorrelationId()).isEqualTo("corr-1");
        assertThat(p.getIdExterno()).isEqualTo("PREF-1");
    }

    @Test
    void linkSemEnderecoOuSemValidadeEhRecusado() {
        Orcamento o = orcamento();
        assertThatThrownBy(() -> Pagamento.aguardando("p", o, "LINK", "X", " ", T0.plusSeconds(60), "s", "c", T0))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> Pagamento.aguardando("p", o, "LINK", "X", "https://x", null, "s", "c", T0))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void aprovarTrocaOIdExternoPeloDoPagamento() {
        Pagamento p = aguardando(T0.plusSeconds(3600));

        p.aprovar("MP-999", T0.plusSeconds(10));

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(p.getIdExterno()).isEqualTo("MP-999");
        assertThat(p.getAtualizadoEm()).isEqualTo(T0.plusSeconds(10));
    }

    @Test
    void soAprovaQuemEstaAguardandoEComIdDoProvedor() {
        Pagamento p = aguardando(T0.plusSeconds(3600));
        assertThatThrownBy(() -> p.aprovar(" ", T0)).isInstanceOf(RegraDeNegocioException.class);
        p.aprovar("MP-1", T0);
        assertThatThrownBy(() -> p.aprovar("MP-2", T0)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void expirarEhIdempotenteMasNaoValeParaPagamentoAprovado() {
        Pagamento p = aguardando(T0.plusSeconds(3600));

        assertThat(p.expirar("venceu", T0)).isTrue();
        assertThat(p.expirar("venceu", T0)).isFalse();
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
        assertThat(p.getMotivo()).isEqualTo("venceu");

        Pagamento pago = aguardando(T0.plusSeconds(3600));
        pago.aprovar("MP-1", T0);
        assertThatThrownBy(() -> pago.expirar("venceu", T0)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void estaVencidoSoQuandoAguardandoEPassouDaValidade() {
        Pagamento p = aguardando(T0.plusSeconds(60));
        assertThat(p.estaVencido(T0)).isFalse();
        assertThat(p.estaVencido(T0.plusSeconds(60))).isTrue();
        p.aprovar("MP-1", T0);
        assertThat(p.estaVencido(T0.plusSeconds(600))).isFalse();
    }

    @Test
    void pagamentoAprovadoPeloLinkPodeSerEstornado() {
        Pagamento p = aguardando(T0.plusSeconds(3600));
        p.aprovar("MP-1", T0);
        p.estornar("compensação", T0.plusSeconds(5));
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
    }

    @Test
    void anotarGuardaOTextoSemMudarOStatus() {
        Pagamento p = aguardando(T0.plusSeconds(60));
        p.expirar("venceu", T0);
        p.anotar("devolvido", T0.plusSeconds(1));
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
        assertThat(p.getMotivo()).isEqualTo("devolvido");
    }
}
