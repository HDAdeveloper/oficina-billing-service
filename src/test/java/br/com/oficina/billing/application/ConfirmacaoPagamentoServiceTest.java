package br.com.oficina.billing.application;

import br.com.oficina.billing.application.port.out.GatewayPagamentoIndisponivelException;
import br.com.oficina.billing.application.port.out.PagamentoGateway.PagamentoConsultado;
import br.com.oficina.billing.application.port.out.PagamentoGateway.PagamentoConsultado.Situacao;
import br.com.oficina.billing.application.service.ConfirmacaoPagamentoService;
import br.com.oficina.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.domain.exception.RegraDeNegocioException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfirmacaoPagamentoServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-22T12:00:00Z");

    private Fakes.OrcamentosMem orcamentos;
    private Fakes.PagamentosMem pagamentos;
    private Fakes.EventosGravador eventos;
    private Fakes.GatewayFake gateway;
    private ConfirmacaoPagamentoService service;
    private Orcamento orcamento;

    @BeforeEach
    void setUp() {
        orcamentos = new Fakes.OrcamentosMem();
        pagamentos = new Fakes.PagamentosMem();
        eventos = new Fakes.EventosGravador();
        gateway = new Fakes.GatewayFake();
        service = new ConfirmacaoPagamentoService(orcamentos, pagamentos, eventos, gateway,
                Clock.fixed(T0, ZoneOffset.UTC));
        orcamento = Orcamento.criar("orc-1", 42L, "52998224725", "ana@x.com", List.of(
                new ItemOrcamento(TipoItem.SERVICO, "S1", "Revisão", 1, new BigDecimal("100.00"))), T0);
        orcamentos.salvar(orcamento);
    }

    private Pagamento aguardando(String id, Instant expiraEm) {
        Pagamento p = Pagamento.aguardando(id, orcamento, "LINK", "PREF-" + id, "https://mp/pagar/" + id, expiraEm,
                "saga-1", "corr-1", T0);
        pagamentos.salvar(p);
        return p;
    }

    private void provedorDiz(String idExterno, String referencia, Situacao situacao) {
        gateway.consultas.put(idExterno, new PagamentoConsultado(idExterno, referencia, situacao, "detalhe"));
    }

    @Test
    void aprovacaoConfirmadaPorConsultaPagaOOrcamentoEAvisaASaga() {
        Pagamento p = aguardando("pag-1", T0.plusSeconds(3600));
        provedorDiz("MP-9", "pag-1", Situacao.APROVADO);

        service.pagamentoAtualizado("MP-9");

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(p.getIdExterno()).isEqualTo("MP-9");
        assertThat(orcamento.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(eventos.tipos()).containsExactly("PAGAMENTO_APROVADO");
        var e = eventos.ultimo();
        assertThat(e.sagaId()).isEqualTo("saga-1");
        assertThat(e.correlationId()).isEqualTo("corr-1");
        assertThat(e.osId()).isEqualTo(42L);
        assertThat(e.payload()).containsEntry("pagamentoId", "pag-1").containsEntry("orcamentoId", "orc-1");
    }

    @Test
    void avisoRepetidoNaoAprovaNemPublicaDeNovo() {
        aguardando("pag-1", T0.plusSeconds(3600));
        provedorDiz("MP-9", "pag-1", Situacao.APROVADO);

        service.pagamentoAtualizado("MP-9");
        service.pagamentoAtualizado("MP-9");

        assertThat(eventos.publicados).hasSize(1);
        assertThat(gateway.estornados).isEmpty();
    }

    @Test
    void pagamentoRecusadoNoProvedorNaoEncerraOLink() {
        Pagamento p = aguardando("pag-1", T0.plusSeconds(3600));
        provedorDiz("MP-8", "pag-1", Situacao.RECUSADO);

        service.pagamentoAtualizado("MP-8");

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.AGUARDANDO);
        assertThat(eventos.publicados).isEmpty();
    }

    @Test
    void pagamentoPendenteNoProvedorEsperaOProximoAviso() {
        Pagamento p = aguardando("pag-1", T0.plusSeconds(3600));
        provedorDiz("MP-7", "pag-1", Situacao.PENDENTE);

        service.pagamentoAtualizado("MP-7");

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.AGUARDANDO);
        assertThat(eventos.publicados).isEmpty();
    }

    @Test
    void pagamentoSemReferenciaOuDeOutroSistemaEhIgnorado() {
        provedorDiz("MP-1", null, Situacao.APROVADO);
        provedorDiz("MP-2", "nao-existe", Situacao.APROVADO);

        service.pagamentoAtualizado("MP-1");
        service.pagamentoAtualizado("MP-2");

        assertThat(eventos.publicados).isEmpty();
        assertThat(gateway.estornados).isEmpty();
    }

    @Test
    void pagamentoDepoisDoVencimentoEhDevolvidoSemMexerNaSaga() {
        Pagamento p = aguardando("pag-1", T0.plusSeconds(3600));
        p.expirar("Link expirou", T0);
        provedorDiz("MP-9", "pag-1", Situacao.APROVADO);

        service.pagamentoAtualizado("MP-9");

        assertThat(gateway.estornados).containsExactly("MP-9");
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
        assertThat(p.getMotivo()).contains("devolvido automaticamente").contains("MP-9");
        assertThat(eventos.publicados).isEmpty();
        assertThat(orcamento.getStatus()).isEqualTo(StatusOrcamento.PENDENTE);
    }

    @Test
    void segundoPagamentoNoMesmoLinkEhDevolvido() {
        aguardando("pag-1", T0.plusSeconds(3600));
        provedorDiz("MP-9", "pag-1", Situacao.APROVADO);
        provedorDiz("MP-10", "pag-1", Situacao.APROVADO);

        service.pagamentoAtualizado("MP-9");
        service.pagamentoAtualizado("MP-10");

        assertThat(gateway.estornados).containsExactly("MP-10");
        assertThat(eventos.publicados).hasSize(1);
    }

    @Test
    void falhaDoProvedorPropagaParaOWebhookSerReenviado() {
        aguardando("pag-1", T0.plusSeconds(3600));

        assertThatThrownBy(() -> service.pagamentoAtualizado("MP-x"))
                .isInstanceOf(GatewayPagamentoIndisponivelException.class);
        assertThat(eventos.publicados).isEmpty();
    }

    @Test
    void falhaNaDevolucaoPropagaParaOWebhookSerReenviado() {
        Pagamento p = aguardando("pag-1", T0.plusSeconds(3600));
        p.expirar("Link expirou", T0);
        provedorDiz("MP-9", "pag-1", Situacao.APROVADO);
        gateway.indisponivelNoEstorno = true;

        assertThatThrownBy(() -> service.pagamentoAtualizado("MP-9"))
                .isInstanceOf(GatewayPagamentoIndisponivelException.class);
    }

    // ------------------------------------------------------------ vencimento

    @Test
    void venceSoOsLinksQuePassaramDaValidadeERespondeRecusadoParaASaga() {
        Pagamento vencido = aguardando("pag-1", T0.minusSeconds(1));
        Orcamento outro = Orcamento.criar("orc-2", 43L, "52998224725", "ana@x.com", List.of(
                new ItemOrcamento(TipoItem.SERVICO, "S1", "Revisão", 1, new BigDecimal("50.00"))), T0);
        orcamentos.salvar(outro);
        Pagamento valido = Pagamento.aguardando("pag-2", outro, "LINK", "PREF-2", "https://mp/pagar/2",
                T0.plusSeconds(600), "saga-2", "corr-2", T0);
        pagamentos.salvar(valido);

        int vencidos = service.expirarVencidos();

        assertThat(vencidos).isEqualTo(1);
        assertThat(vencido.getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
        assertThat(valido.getStatus()).isEqualTo(StatusPagamento.AGUARDANDO);
        assertThat(eventos.tipos()).containsExactly("PAGAMENTO_RECUSADO");
        assertThat(eventos.ultimo().sagaId()).isEqualTo("saga-1");
        assertThat(eventos.ultimo().payload()).containsEntry("orcamentoId", "orc-1");
        assertThat((String) eventos.ultimo().payload().get("motivo")).contains("expirou");
    }

    @Test
    void venceDeNovoNaoRepeteOEvento() {
        aguardando("pag-1", T0.minusSeconds(1));

        service.expirarVencidos();
        int segunda = service.expirarVencidos();

        assertThat(segunda).isZero();
        assertThat(eventos.publicados).hasSize(1);
    }

    // ------------------------------------------------------------ simulador

    @Test
    void aprovarSimuladoUsaOMesmoCaminhoDoWebhook() {
        Pagamento p = aguardando("pag-1", T0.plusSeconds(3600));

        service.aprovarSimulado("pag-1");

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(p.getIdExterno()).isEqualTo("SIM-pag-1");
        assertThat(eventos.tipos()).containsExactly("PAGAMENTO_APROVADO");
    }

    @Test
    void expirarAgoraVenceOLinkEExigePagamentoAguardando() {
        Pagamento p = aguardando("pag-1", T0.plusSeconds(3600));

        service.expirarAgora("pag-1");

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
        assertThat(eventos.tipos()).containsExactly("PAGAMENTO_RECUSADO");
        assertThatThrownBy(() -> service.expirarAgora("pag-1")).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> service.expirarAgora("nao-existe")).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.aprovarSimulado("nao-existe")).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
