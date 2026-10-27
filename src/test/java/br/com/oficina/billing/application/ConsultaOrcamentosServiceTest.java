package br.com.oficina.billing.application;

import br.com.oficina.billing.application.service.ConsultaOrcamentosService;
import br.com.oficina.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.domain.model.ItemOrcamento;
import br.com.oficina.billing.domain.model.Orcamento;
import br.com.oficina.billing.domain.model.Pagamento;
import br.com.oficina.billing.domain.model.TipoItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultaOrcamentosServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-22T12:00:00Z");

    private Fakes.OrcamentosMem orcamentos;
    private Fakes.PagamentosMem pagamentos;
    private ConsultaOrcamentosService consulta;

    @BeforeEach
    void setUp() {
        orcamentos = new Fakes.OrcamentosMem();
        pagamentos = new Fakes.PagamentosMem();
        consulta = new ConsultaOrcamentosService(orcamentos, pagamentos);
    }

    private Orcamento orcamento(String id, long osId, Instant criadoEm) {
        Orcamento o = Orcamento.criar(id, osId, 1L, null,
                List.of(new ItemOrcamento(TipoItem.SERVICO, "S", "Serviço", 1, BigDecimal.TEN)), criadoEm);
        orcamentos.salvar(o);
        return o;
    }

    @Test
    void buscaPorIdOuLancaNaoEncontrado() {
        orcamento("o-1", 1L, T0);
        assertThat(consulta.buscarPorId("o-1").getOsId()).isEqualTo(1L);
        assertThatThrownBy(() -> consulta.buscarPorId("o-9")).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void listaPorOsDoMaisNovoParaOMaisAntigo() {
        orcamento("o-1", 5L, T0);
        orcamento("o-2", 5L, T0.plusSeconds(10));
        orcamento("o-3", 6L, T0);

        assertThat(consulta.listarPorOs(5L)).extracting(Orcamento::getId).containsExactly("o-2", "o-1");
        assertThat(consulta.listarPorOs(99L)).isEmpty();
    }

    @Test
    void listaPagamentosDoOrcamento() {
        Orcamento o = orcamento("o-1", 5L, T0);
        pagamentos.salvar(Pagamento.recusado("p-1", o, "FUND", "Saldo insuficiente", T0));
        pagamentos.salvar(Pagamento.aprovado("p-2", o, "APRO", "EXT", T0.plusSeconds(5)));

        assertThat(consulta.listarPagamentos("o-1")).extracting(Pagamento::getId).containsExactly("p-2", "p-1");
        assertThatThrownBy(() -> consulta.listarPagamentos("o-9")).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
