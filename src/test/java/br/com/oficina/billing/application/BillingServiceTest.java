package br.com.oficina.billing.application;

import br.com.oficina.billing.application.port.in.ComandoBilling;
import br.com.oficina.billing.application.port.in.ComandoBilling.CancelarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.CobrarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.CriarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.EstornarPagamento;
import br.com.oficina.billing.application.port.in.ContextoMensagem;
import br.com.oficina.billing.application.port.out.GatewayPagamentoIndisponivelException;
import br.com.oficina.billing.application.port.out.PagamentoGateway.ResultadoCobranca;
import br.com.oficina.billing.application.service.BillingService;
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

class BillingServiceTest {

    private static final long OS = 42L;
    private static final BigDecimal TOTAL = new BigDecimal("251.00");

    private Fakes.OrcamentosMem orcamentos;
    private Fakes.PagamentosMem pagamentos;
    private Fakes.ProcessadasMem processadas;
    private Fakes.EventosGravador eventos;
    private Fakes.GatewayFake gateway;
    private BillingService service;
    private int mensagens;

    @BeforeEach
    void setUp() {
        orcamentos = new Fakes.OrcamentosMem();
        pagamentos = new Fakes.PagamentosMem();
        processadas = new Fakes.ProcessadasMem();
        eventos = new Fakes.EventosGravador();
        gateway = new Fakes.GatewayFake();
        service = new BillingService(orcamentos, pagamentos, processadas, eventos, gateway,
                new Fakes.IdsSequenciais(), Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC));
        mensagens = 0;
    }

    // ------------------------------------------------------------ apoio

    private ContextoMensagem novaMensagem() {
        return new ContextoMensagem("msg-" + (++mensagens), "saga-1", OS, "corr-1");
    }

    private static List<ItemOrcamento> itens() {
        return List.of(
                new ItemOrcamento(TipoItem.SERVICO, "SRV-1", "Alinhamento", 1, new BigDecimal("90.00")),
                new ItemOrcamento(TipoItem.PECA, "PAST-1", "Pastilha", 2, new BigDecimal("80.50")));
    }

    private void criar() {
        service.processar(novaMensagem(), new CriarOrcamento("52998224725", "ana@x.com", itens(), TOTAL));
    }

    private void cobrar(String orcamentoId, String metodo) {
        service.processar(novaMensagem(), new CobrarOrcamento(orcamentoId, TOTAL, metodo));
    }

    private Orcamento orcamentoCriado() {
        criar();
        return orcamentos.dados.values().iterator().next();
    }

    private Orcamento orcamentoPago() {
        Orcamento o = orcamentoCriado();
        cobrar(o.getId(), "APRO");
        return o;
    }

    // ------------------------------------------------------------ CRIAR_ORCAMENTO

    @Test
    void criaOrcamentoERespondeComEventoCriado() {
        criar();

        assertThat(orcamentos.dados).hasSize(1);
        Orcamento o = orcamentos.dados.values().iterator().next();
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PENDENTE);
        assertThat(o.getValorTotal()).isEqualTo(TOTAL);
        assertThat(eventos.tipos()).containsExactly("ORCAMENTO_CRIADO");
        assertThat(eventos.ultimo().payload()).containsEntry("orcamentoId", o.getId())
                .containsEntry("valorTotal", TOTAL);
    }

    @Test
    void eventosCarregamOContextoDaSaga() {
        criar();
        var e = eventos.ultimo();
        assertThat(e.osId()).isEqualTo(OS);
        assertThat(e.sagaId()).isEqualTo("saga-1");
        assertThat(e.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void mensagemRepetidaComOMesmoMessageIdEhIgnorada() {
        ContextoMensagem ctx = novaMensagem();
        CriarOrcamento cmd = new CriarOrcamento("52998224725", "ana@x.com", itens(), TOTAL);

        service.processar(ctx, cmd);
        service.processar(ctx, cmd);

        assertThat(orcamentos.dados).hasSize(1);
        assertThat(eventos.publicados).hasSize(1);
    }

    @Test
    void pedidoRepetidoEmOutraMensagemReaproveitaOMesmoOrcamento() {
        criar();
        criar();

        assertThat(orcamentos.dados).hasSize(1);
        assertThat(eventos.tipos()).containsExactly("ORCAMENTO_CRIADO", "ORCAMENTO_CRIADO");
        assertThat(eventos.publicados.get(0).payload().get("orcamentoId"))
                .isEqualTo(eventos.publicados.get(1).payload().get("orcamentoId"));
    }

    @Test
    void itensDiferentesSubstituemOOrcamentoPendente() {
        criar();
        List<ItemOrcamento> outros = List.of(
                new ItemOrcamento(TipoItem.SERVICO, "SRV-2", "Balanceamento", 1, new BigDecimal("50.00")));

        service.processar(novaMensagem(), new CriarOrcamento("52998224725", "ana@x.com", outros, new BigDecimal("50.00")));

        assertThat(orcamentos.dados).hasSize(2);
        assertThat(orcamentos.dados.get("id-1").getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(orcamentos.dados.get("id-2").getStatus()).isEqualTo(StatusOrcamento.PENDENTE);
        assertThat(eventos.ultimo().type()).isEqualTo("ORCAMENTO_CRIADO");
    }

    @Test
    void valorTotalDivergenteResultaEmFalha() {
        service.processar(novaMensagem(), new CriarOrcamento("52998224725", null, itens(), new BigDecimal("999.00")));

        assertThat(orcamentos.dados).isEmpty();
        assertThat(eventos.tipos()).containsExactly("ORCAMENTO_FALHOU");
        assertThat((String) eventos.ultimo().payload().get("motivo")).contains("divergente");
    }

    @Test
    void semItensResultaEmFalha() {
        service.processar(novaMensagem(), new CriarOrcamento("52998224725", null, List.of(), BigDecimal.ZERO));
        service.processar(novaMensagem(), new CriarOrcamento("52998224725", null, null, null));

        assertThat(eventos.tipos()).containsExactly("ORCAMENTO_FALHOU", "ORCAMENTO_FALHOU");
        assertThat(orcamentos.dados).isEmpty();
    }

    @Test
    void osSemIdResultaEmFalha() {
        service.processar(new ContextoMensagem("m", "s", null, "c"), new CriarOrcamento("52998224725", null, itens(), TOTAL));
        assertThat(eventos.tipos()).containsExactly("ORCAMENTO_FALHOU");
    }

    @Test
    void pedidoComItensInvalidosRespondeFalhaComOMotivo() {
        service.processar(novaMensagem(), new ComandoBilling.OrcamentoInvalido("Quantidade do item deve ser maior que zero"));

        assertThat(eventos.tipos()).containsExactly("ORCAMENTO_FALHOU");
        assertThat((String) eventos.ultimo().payload().get("motivo")).contains("Quantidade");
        assertThat(orcamentos.dados).isEmpty();
    }

    @Test
    void osComOrcamentoPagoNaoGeraOutro() {
        orcamentoPago();
        eventos.publicados.clear();

        criar();

        assertThat(eventos.tipos()).containsExactly("ORCAMENTO_FALHOU");
        assertThat((String) eventos.ultimo().payload().get("motivo")).contains("já tem um orçamento pago");
        assertThat(orcamentos.dados).hasSize(1);
    }

    // ------------------------------------------------------------ COBRAR_ORCAMENTO

    @Test
    void cobrancaAprovadaMarcaPagoERegistraPagamento() {
        Orcamento o = orcamentoCriado();
        eventos.publicados.clear();

        cobrar(o.getId(), "APRO");

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        Pagamento p = pagamentos.dados.values().iterator().next();
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(p.getIdExterno()).isEqualTo("EXT-1");
        assertThat(p.getValor()).isEqualTo(TOTAL);
        assertThat(eventos.tipos()).containsExactly("PAGAMENTO_APROVADO");
        assertThat(eventos.ultimo().payload()).containsEntry("pagamentoId", p.getId())
                .containsEntry("orcamentoId", o.getId());
    }

    @Test
    void cobrancaRepetidaNaoCobraDeNovo() {
        Orcamento o = orcamentoPago();
        String pagamentoId = pagamentos.dados.keySet().iterator().next();

        cobrar(o.getId(), "APRO");

        assertThat(gateway.cobrancas).isEqualTo(1);
        assertThat(pagamentos.dados).hasSize(1);
        assertThat(eventos.tipos()).endsWith("PAGAMENTO_APROVADO", "PAGAMENTO_APROVADO");
        assertThat(eventos.ultimo().payload()).containsEntry("pagamentoId", pagamentoId);
    }

    @Test
    void cobrancaRecusadaMantemOrcamentoPendenteERegistraTentativa() {
        Orcamento o = orcamentoCriado();
        gateway.proximoResultado = new ResultadoCobranca.Recusada("Saldo insuficiente");
        eventos.publicados.clear();

        cobrar(o.getId(), "FUND");

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PENDENTE);
        assertThat(pagamentos.dados.values()).singleElement()
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo(StatusPagamento.RECUSADO));
        assertThat(eventos.tipos()).containsExactly("PAGAMENTO_RECUSADO");
        assertThat(eventos.ultimo().payload()).containsEntry("motivo", "Saldo insuficiente");
    }

    @Test
    void orcamentoInexistenteResultaEmPagamentoRecusado() {
        cobrar("nao-existe", "APRO");

        assertThat(eventos.tipos()).containsExactly("PAGAMENTO_RECUSADO");
        assertThat(gateway.cobrancas).isZero();
    }

    @Test
    void valorDivergenteNaoChegaAoProvedor() {
        Orcamento o = orcamentoCriado();
        eventos.publicados.clear();

        service.processar(novaMensagem(), new CobrarOrcamento(o.getId(), new BigDecimal("1.00"), "APRO"));

        assertThat(eventos.tipos()).containsExactly("PAGAMENTO_RECUSADO");
        assertThat(gateway.cobrancas).isZero();
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PENDENTE);
    }

    @Test
    void orcamentoCanceladoNaoPodeSerCobrado() {
        Orcamento o = orcamentoCriado();
        service.processar(novaMensagem(), new CancelarOrcamento(o.getId(), "recusado"));
        eventos.publicados.clear();

        cobrar(o.getId(), "APRO");

        assertThat(eventos.tipos()).containsExactly("PAGAMENTO_RECUSADO");
        assertThat((String) eventos.ultimo().payload().get("motivo")).contains("CANCELADO");
        assertThat(gateway.cobrancas).isZero();
    }

    @Test
    void provedorForaDoArPropagaExcecaoSemEfeitos() {
        Orcamento o = orcamentoCriado();
        eventos.publicados.clear();
        gateway.indisponivelNaCobranca = true;
        ContextoMensagem ctx = novaMensagem();

        assertThatThrownBy(() -> service.processar(ctx, new CobrarOrcamento(o.getId(), TOTAL, "ERRO")))
                .isInstanceOf(GatewayPagamentoIndisponivelException.class);

        assertThat(eventos.publicados).isEmpty();
        assertThat(pagamentos.dados).isEmpty();
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PENDENTE);
        assertThat(processadas.jaProcessada(ctx.messageId())).isFalse(); // será tentada de novo
    }

    // ------------------------------------------------------------ CANCELAR_ORCAMENTO

    @Test
    void cancelaOrcamentoPendente() {
        Orcamento o = orcamentoCriado();
        eventos.publicados.clear();

        service.processar(novaMensagem(), new CancelarOrcamento(o.getId(), "pagamento recusado"));

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(o.getMotivoCancelamento()).isEqualTo("pagamento recusado");
        assertThat(eventos.tipos()).containsExactly("ORCAMENTO_CANCELADO");
        assertThat(eventos.ultimo().payload()).containsEntry("orcamentoId", o.getId());
    }

    @Test
    void cancelamentoRepetidoRespondeDeNovoSemMudarNada() {
        Orcamento o = orcamentoCriado();
        service.processar(novaMensagem(), new CancelarOrcamento(o.getId(), "primeiro"));
        service.processar(novaMensagem(), new CancelarOrcamento(o.getId(), "segundo"));

        assertThat(o.getMotivoCancelamento()).isEqualTo("primeiro");
        assertThat(eventos.tipos()).endsWith("ORCAMENTO_CANCELADO", "ORCAMENTO_CANCELADO");
    }

    @Test
    void cancelarOrcamentoInexistenteConfirmaMesmoAssim() {
        service.processar(novaMensagem(), new CancelarOrcamento("sumiu", "x"));
        assertThat(eventos.tipos()).containsExactly("ORCAMENTO_CANCELADO");
    }

    @Test
    void orcamentoPagoNaoSeCancelaENaoGeraEvento() {
        Orcamento o = orcamentoPago();
        eventos.publicados.clear();
        ContextoMensagem ctx = novaMensagem();

        service.processar(ctx, new CancelarOrcamento(o.getId(), "x"));

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(eventos.publicados).isEmpty();
        assertThat(processadas.jaProcessada(ctx.messageId())).isTrue(); // não adianta repetir
    }

    // ------------------------------------------------------------ ESTORNAR_PAGAMENTO

    @Test
    void estornaPagamentoAprovado() {
        Orcamento o = orcamentoPago();
        Pagamento p = pagamentos.dados.values().iterator().next();
        eventos.publicados.clear();

        service.processar(novaMensagem(), new EstornarPagamento(p.getId(), "estoque insuficiente"));

        assertThat(gateway.estornados).containsExactly("EXT-1");
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.ESTORNADO);
        assertThat(eventos.tipos()).containsExactly("PAGAMENTO_ESTORNADO");
        assertThat(eventos.ultimo().payload()).containsEntry("pagamentoId", p.getId());
    }

    @Test
    void estornoRepetidoNaoDevolveDeNovo() {
        orcamentoPago();
        Pagamento p = pagamentos.dados.values().iterator().next();

        service.processar(novaMensagem(), new EstornarPagamento(p.getId(), "a"));
        service.processar(novaMensagem(), new EstornarPagamento(p.getId(), "b"));

        assertThat(gateway.estornados).hasSize(1);
        assertThat(eventos.tipos()).endsWith("PAGAMENTO_ESTORNADO", "PAGAMENTO_ESTORNADO");
    }

    @Test
    void estornoComProvedorForaDoArNaoAlteraNada() {
        Orcamento o = orcamentoPago();
        Pagamento p = pagamentos.dados.values().iterator().next();
        eventos.publicados.clear();
        gateway.indisponivelNoEstorno = true;
        ContextoMensagem ctx = novaMensagem();

        assertThatThrownBy(() -> service.processar(ctx, new EstornarPagamento(p.getId(), "x")))
                .isInstanceOf(GatewayPagamentoIndisponivelException.class);

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(eventos.publicados).isEmpty();
        assertThat(processadas.jaProcessada(ctx.messageId())).isFalse();
    }

    @Test
    void estornoDePagamentoInexistenteOuRecusadoFalha() {
        assertThatThrownBy(() -> service.processar(novaMensagem(), new EstornarPagamento("nada", "x")))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        Orcamento o = orcamentoCriado();
        gateway.proximoResultado = new ResultadoCobranca.Recusada("Saldo insuficiente");
        cobrar(o.getId(), "FUND");
        Pagamento recusado = pagamentos.dados.values().iterator().next();
        assertThatThrownBy(() -> service.processar(novaMensagem(), new EstornarPagamento(recusado.getId(), "x")))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    // ------------------------------------------------------------ fluxos completos da Saga

    @Test
    void compensacaoAPagamentoRecusadoCancelaOOrcamento() {
        Orcamento o = orcamentoCriado();
        gateway.proximoResultado = new ResultadoCobranca.Recusada("Saldo insuficiente");

        cobrar(o.getId(), "FUND");
        service.processar(novaMensagem(), new CancelarOrcamento(o.getId(), "Saldo insuficiente"));

        assertThat(eventos.tipos()).containsExactly("ORCAMENTO_CRIADO", "PAGAMENTO_RECUSADO", "ORCAMENTO_CANCELADO");
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
    }

    @Test
    void compensacaoBEstoqueInsuficienteEstornaOPagamento() {
        Orcamento o = orcamentoPago();
        Pagamento p = pagamentos.dados.values().iterator().next();

        service.processar(novaMensagem(), new EstornarPagamento(p.getId(), "Estoque insuficiente"));

        assertThat(eventos.tipos()).containsExactly("ORCAMENTO_CRIADO", "PAGAMENTO_APROVADO", "PAGAMENTO_ESTORNADO");
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.ESTORNADO);
    }

    @Test
    void novoOrcamentoDepoisDeCancelarOAnterior() {
        Orcamento antigo = orcamentoCriado();
        service.processar(novaMensagem(), new CancelarOrcamento(antigo.getId(), "recusado"));

        criar();

        // Não usa buscarPorOs: com o relógio fixo dos testes os dois têm o mesmo criadoEm
        assertThat(orcamentos.dados).hasSize(2);
        assertThat(orcamentos.dados.get(antigo.getId()).getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(orcamentos.dados.values()).filteredOn(o -> o.getStatus() == StatusOrcamento.PENDENTE).hasSize(1);
    }
}
