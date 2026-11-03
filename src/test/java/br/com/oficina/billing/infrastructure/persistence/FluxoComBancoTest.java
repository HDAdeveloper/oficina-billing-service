package br.com.oficina.billing.infrastructure.persistence;

import br.com.oficina.billing.application.port.in.ComandoBilling.CancelarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.CobrarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.CriarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.EstornarPagamento;
import br.com.oficina.billing.application.port.in.ConfirmacaoPagamentos;
import br.com.oficina.billing.application.port.in.ConsultaOrcamentos;
import br.com.oficina.billing.application.port.in.ContextoMensagem;
import br.com.oficina.billing.application.port.in.ProcessadorComandos;
import br.com.oficina.billing.domain.model.ItemOrcamento;
import br.com.oficina.billing.domain.model.Orcamento;
import br.com.oficina.billing.domain.model.Pagamento;
import br.com.oficina.billing.domain.model.StatusOrcamento;
import br.com.oficina.billing.domain.model.StatusPagamento;
import br.com.oficina.billing.domain.model.TipoItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** Casos de uso reais + Postgres real: confere estado e eventos da outbox gravados juntos. */
@SpringBootTest
class FluxoComBancoTest extends PostgresTestBase {

    private static final AtomicLong OS = new AtomicLong(5000);

    @Autowired ProcessadorComandos processador;
    @Autowired ConsultaOrcamentos consulta;
    @Autowired ConfirmacaoPagamentos confirmacao;
    @Autowired OutboxEventoJpa outbox;
    @Autowired ObjectMapper json;

    private static ContextoMensagem ctx(long osId) {
        return new ContextoMensagem(UUID.randomUUID().toString(), "saga-" + osId, osId, "corr-" + osId);
    }

    private static List<ItemOrcamento> itens() {
        return List.of(new ItemOrcamento(TipoItem.SERVICO, "S1", "Revisão", 1, new BigDecimal("200.00")));
    }

    private String criar(long osId) throws Exception {
        processador.processar(ctx(osId), new CriarOrcamento("52998224725", "ana@x.com", itens(), new BigDecimal("200.00")));
        List<OutboxEventoEntity> eventos = outbox.findByOsIdOrderByIdAsc(osId);
        OutboxEventoEntity criado = eventos.get(eventos.size() - 1);
        assertThat(criado.getTipo()).isEqualTo("ORCAMENTO_CRIADO");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = json.readValue(criado.getPayload(), Map.class);
        return (String) payload.get("orcamentoId");
    }

    private List<String> tipos(long osId) {
        return outbox.findByOsIdOrderByIdAsc(osId).stream().map(OutboxEventoEntity::getTipo).toList();
    }

    @Test
    void criarECobrarGravamEstadoEEventosNaOutbox() throws Exception {
        long os = OS.incrementAndGet();
        String orcamentoId = criar(os);

        processador.processar(ctx(os), new CobrarOrcamento(orcamentoId, new BigDecimal("200.00"), "APRO"));

        assertThat(consulta.buscarPorId(orcamentoId).getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(tipos(os)).containsExactly("ORCAMENTO_CRIADO", "PAGAMENTO_APROVADO");
        assertThat(outbox.findByPublicadoEmIsNullOrderByIdAsc(org.springframework.data.domain.PageRequest.of(0, 1000))
                .stream().filter(e -> os == e.getOsId())).hasSize(2);
    }

    @Test
    void mensagemRepetidaNaoGeraNovoEvento() throws Exception {
        long os = OS.incrementAndGet();
        ContextoMensagem mesma = ctx(os);
        CriarOrcamento cmd = new CriarOrcamento("52998224725", "ana@x.com", itens(), new BigDecimal("200.00"));

        processador.processar(mesma, cmd);
        processador.processar(mesma, cmd);

        assertThat(tipos(os)).containsExactly("ORCAMENTO_CRIADO");
        assertThat(consulta.listarPorOs(os)).hasSize(1);
    }

    @Test
    void pagamentoRecusadoDeixaOrcamentoPendente() throws Exception {
        long os = OS.incrementAndGet();
        String orcamentoId = criar(os);

        processador.processar(ctx(os), new CobrarOrcamento(orcamentoId, new BigDecimal("200.00"), "FUND"));

        assertThat(consulta.buscarPorId(orcamentoId).getStatus()).isEqualTo(StatusOrcamento.PENDENTE);
        assertThat(tipos(os)).containsExactly("ORCAMENTO_CRIADO", "PAGAMENTO_RECUSADO");
    }

    @Test
    void compensacaoEstornaOPagamento() throws Exception {
        long os = OS.incrementAndGet();
        String orcamentoId = criar(os);
        processador.processar(ctx(os), new CobrarOrcamento(orcamentoId, new BigDecimal("200.00"), "APRO"));
        Pagamento pago = consulta.listarPagamentos(orcamentoId).get(0);

        processador.processar(ctx(os), new EstornarPagamento(pago.getId(), "falha na execução"));

        assertThat(consulta.listarPagamentos(orcamentoId).get(0).getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(consulta.buscarPorId(orcamentoId).getStatus()).isEqualTo(StatusOrcamento.ESTORNADO);
        assertThat(tipos(os)).containsExactly("ORCAMENTO_CRIADO", "PAGAMENTO_APROVADO", "PAGAMENTO_ESTORNADO");
    }

    @Test
    void compensacaoCancelaOrcamentoPendente() throws Exception {
        long os = OS.incrementAndGet();
        String orcamentoId = criar(os);

        processador.processar(ctx(os), new CancelarOrcamento(orcamentoId, "cliente recusou"));

        Orcamento o = consulta.buscarPorId(orcamentoId);
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(tipos(os)).containsExactly("ORCAMENTO_CRIADO", "ORCAMENTO_CANCELADO");
    }

    @Test
    void pagamentoPorLinkFicaAguardandoEAprovacaoPublicaOEventoComASagaGuardada() throws Exception {
        long os = OS.incrementAndGet();
        String orcamentoId = criar(os);

        processador.processar(ctx(os), new CobrarOrcamento(orcamentoId, new BigDecimal("200.00"), "LINK"));

        Pagamento aguardando = consulta.listarPagamentos(orcamentoId).get(0);
        assertThat(aguardando.getStatus()).isEqualTo(StatusPagamento.AGUARDANDO);
        assertThat(aguardando.getLinkPagamento()).isNotBlank();
        assertThat(aguardando.getSagaId()).isEqualTo("saga-" + os);
        assertThat(consulta.buscarPorId(orcamentoId).getStatus()).isEqualTo(StatusOrcamento.PENDENTE);
        assertThat(tipos(os)).containsExactly("ORCAMENTO_CRIADO", "LINK_PAGAMENTO_GERADO");

        confirmacao.aprovarSimulado(aguardando.getId()); // o aviso do provedor chega depois, fora da Saga

        assertThat(consulta.listarPagamentos(orcamentoId).get(0).getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(consulta.buscarPorId(orcamentoId).getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(tipos(os)).containsExactly("ORCAMENTO_CRIADO", "LINK_PAGAMENTO_GERADO", "PAGAMENTO_APROVADO");
        assertThat(outbox.findByOsIdOrderByIdAsc(os).get(2).getSagaId()).isEqualTo("saga-" + os);
    }

    @Test
    void linkVencidoPublicaPagamentoRecusadoELiberaOCancelamentoDoOrcamento() throws Exception {
        long os = OS.incrementAndGet();
        String orcamentoId = criar(os);
        processador.processar(ctx(os), new CobrarOrcamento(orcamentoId, new BigDecimal("200.00"), "LINK"));
        Pagamento aguardando = consulta.listarPagamentos(orcamentoId).get(0);

        confirmacao.expirarAgora(aguardando.getId());
        processador.processar(ctx(os), new CancelarOrcamento(orcamentoId, "Link de pagamento expirou"));

        assertThat(consulta.listarPagamentos(orcamentoId).get(0).getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
        assertThat(consulta.buscarPorId(orcamentoId).getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(tipos(os)).containsExactly("ORCAMENTO_CRIADO", "LINK_PAGAMENTO_GERADO", "PAGAMENTO_RECUSADO",
                "ORCAMENTO_CANCELADO");
    }
}
