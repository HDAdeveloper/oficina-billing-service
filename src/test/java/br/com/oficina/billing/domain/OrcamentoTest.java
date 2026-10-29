package br.com.oficina.billing.domain;

import br.com.oficina.billing.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.domain.model.ItemOrcamento;
import br.com.oficina.billing.domain.model.Orcamento;
import br.com.oficina.billing.domain.model.Pagamento;
import br.com.oficina.billing.domain.model.StatusOrcamento;
import br.com.oficina.billing.domain.model.StatusPagamento;
import br.com.oficina.billing.domain.model.TipoItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrcamentoTest {

    private static final Instant T0 = Instant.parse("2026-10-22T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    private static ItemOrcamento servico() {
        return new ItemOrcamento(TipoItem.SERVICO, "SRV-1", "Alinhamento", 1, new BigDecimal("90"));
    }

    private static ItemOrcamento peca() {
        return new ItemOrcamento(TipoItem.PECA, "PAST-1", "Pastilha", 2, new BigDecimal("80.5"));
    }

    private static Orcamento novo() {
        return Orcamento.criar("orc-1", 42L, "52998224725", "ana@x.com", List.of(servico(), peca()), T0);
    }

    // ------------------------------------------------------------ item

    @Test
    void itemNormalizaEscalaESomaSubtotal() {
        ItemOrcamento i = peca();
        assertThat(i.precoUnitario()).isEqualTo(new BigDecimal("80.50"));
        assertThat(i.subtotal()).isEqualTo(new BigDecimal("161.00"));
        assertThat(servico()).isEqualTo(
                new ItemOrcamento(TipoItem.SERVICO, " SRV-1 ", " Alinhamento ", 1, new BigDecimal("90.00")));
    }

    @Test
    void itemInvalidoEhRejeitado() {
        BigDecimal dez = BigDecimal.TEN;
        assertThatThrownBy(() -> new ItemOrcamento(null, "A", "d", 1, dez)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, " ", "d", 1, dez)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, "A", null, 1, dez)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, "A", "d", 0, dez)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, "A", "d", 1, null)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, "A", "d", 1, new BigDecimal("-1")))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    // ------------------------------------------------------------ criação

    @Test
    void criaOrcamentoPendenteComTotalCalculado() {
        Orcamento o = novo();
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PENDENTE);
        assertThat(o.getValorTotal()).isEqualTo(new BigDecimal("251.00"));
        assertThat(o.getOsId()).isEqualTo(42L);
        assertThat(o.getItens()).hasSize(2);
        assertThat(o.estaAtivo()).isTrue();
        assertThat(o.getCriadoEm()).isEqualTo(T0);
    }

    @Test
    void naoCriaSemIdOsOuItens() {
        List<ItemOrcamento> itens = List.of(servico());
        assertThatThrownBy(() -> Orcamento.criar(" ", 1L, "52998224725", null, itens, T0)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> Orcamento.criar("o", null, "52998224725", null, itens, T0)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> Orcamento.criar("o", 1L, "52998224725", null, List.of(), T0)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> Orcamento.criar("o", 1L, "52998224725", null, null, T0)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void comparaItensIgnorandoComoOValorChegou() {
        Orcamento o = novo();
        assertThat(o.temOsMesmosItens(List.of(
                new ItemOrcamento(TipoItem.SERVICO, "SRV-1", "Alinhamento", 1, new BigDecimal("90.00")),
                new ItemOrcamento(TipoItem.PECA, "PAST-1", "Pastilha", 2, new BigDecimal("80.50"))))).isTrue();
        assertThat(o.temOsMesmosItens(List.of(servico()))).isFalse();
    }

    // ------------------------------------------------------------ transições

    @Test
    void pagamentoAprovadoMarcaComoPago() {
        Orcamento o = novo();
        o.registrarPagamentoAprovado(T1);
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(o.getAtualizadoEm()).isEqualTo(T1);
        assertThatThrownBy(() -> o.registrarPagamentoAprovado(T1)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void cancelaPendenteEEhIdempotente() {
        Orcamento o = novo();
        assertThat(o.cancelar("recusado", T1)).isTrue();
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(o.getMotivoCancelamento()).isEqualTo("recusado");
        assertThat(o.estaAtivo()).isFalse();
        assertThat(o.cancelar("de novo", T1)).isFalse();
        assertThat(o.getMotivoCancelamento()).isEqualTo("recusado");
    }

    @Test
    void naoCancelaOrcamentoPagoOuEstornado() {
        Orcamento pago = novo();
        pago.registrarPagamentoAprovado(T1);
        assertThatThrownBy(() -> pago.cancelar("x", T1))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("estorne");

        pago.estornar(T1);
        assertThatThrownBy(() -> pago.cancelar("x", T1)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void estornaSomenteOrcamentoPago() {
        Orcamento o = novo();
        assertThatThrownBy(() -> o.estornar(T1)).isInstanceOf(RegraDeNegocioException.class);
        o.registrarPagamentoAprovado(T1);
        o.estornar(T1.plusSeconds(1));
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.ESTORNADO);
        assertThat(o.estaAtivo()).isFalse();
    }

    @Test
    void reconstituirPreservaOEstado() {
        Orcamento o = Orcamento.reconstituir("orc-9", 5L, "52998224725", "a@x.com", List.of(servico()),
                StatusOrcamento.CANCELADO, "motivo", T0, T1);
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(o.getValorTotal()).isEqualTo(new BigDecimal("90.00"));
        assertThat(o.getClienteId()).isEqualTo("52998224725");
        assertThat(o.getEmailCliente()).isEqualTo("a@x.com");
        assertThat(o.getAtualizadoEm()).isEqualTo(T1);
    }

    // ------------------------------------------------------------ pagamento

    @Test
    void pagamentoAprovadoPodeSerEstornadoUmaVez() {
        Orcamento o = novo();
        Pagamento p = Pagamento.aprovado("pag-1", o, "APRO", "EXT-1", T0);
        assertThat(p.getValor()).isEqualTo(o.getValorTotal());
        assertThat(p.getOrcamentoId()).isEqualTo("orc-1");
        assertThat(p.getOsId()).isEqualTo(42L);
        assertThat(p.getIdExterno()).isEqualTo("EXT-1");

        p.estornar("estoque insuficiente", T1);
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(p.getMotivo()).isEqualTo("estoque insuficiente");
        assertThatThrownBy(() -> p.estornar("outra vez", T1)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void pagamentoRecusadoNaoPodeSerEstornado() {
        Pagamento p = Pagamento.recusado("pag-2", novo(), "FUND", "Saldo insuficiente", T0);
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(p.getIdExterno()).isNull();
        assertThat(p.getMotivo()).isEqualTo("Saldo insuficiente");
        assertThatThrownBy(() -> p.estornar("x", T1)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void pagamentoReconstituidoMantemOsDados() {
        Pagamento p = Pagamento.reconstituir("pag-3", "orc-1", 42L, new BigDecimal("10.00"), "PIX",
                StatusPagamento.APROVADO, "EXT", null, T0, T1);
        assertThat(p.getMetodo()).isEqualTo("PIX");
        assertThat(p.getCriadoEm()).isEqualTo(T0);
        assertThat(p.getAtualizadoEm()).isEqualTo(T1);
    }
}
