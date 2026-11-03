package br.com.oficina.billing.infrastructure.persistence;

import br.com.oficina.billing.domain.model.ItemOrcamento;
import br.com.oficina.billing.infrastructure.config.ApplicacaoConfig;
import br.com.oficina.billing.domain.model.Orcamento;
import br.com.oficina.billing.domain.model.Pagamento;
import br.com.oficina.billing.domain.model.StatusOrcamento;
import br.com.oficina.billing.domain.model.StatusPagamento;
import br.com.oficina.billing.domain.model.TipoItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// O ApplicacaoConfig (casos de uso) é @Configuration e entraria pelo component scan; aqui só queremos o JPA
@DataJpaTest(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = ApplicacaoConfig.class))
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrcamentoRepositoryAdapter.class, PagamentoRepositoryAdapter.class,
        MensagemProcessadaRepositoryAdapter.class})
class RepositoriosPersistenciaTest extends PostgresTestBase {

    private static final Instant T0 = Instant.parse("2026-10-22T12:00:00Z");

    @Autowired OrcamentoRepositoryAdapter orcamentos;
    @Autowired PagamentoRepositoryAdapter pagamentos;
    @Autowired MensagemProcessadaRepositoryAdapter processadas;

    private static List<ItemOrcamento> itens() {
        return List.of(
                new ItemOrcamento(TipoItem.SERVICO, "S1", "Troca de óleo", 1, new BigDecimal("80")),
                new ItemOrcamento(TipoItem.PECA, "P1", "Filtro", 2, new BigDecimal("25.50")));
    }

    private static Orcamento novo(long osId) {
        return Orcamento.criar(UUID.randomUUID().toString(), osId, "52998224725", "ana@x.com", itens(), T0);
    }

    @Test
    void salvaOrcamentoERecuperaIgual() {
        Orcamento o = novo(1001L);
        orcamentos.salvar(o);

        Orcamento lido = orcamentos.buscarPorId(o.getId()).orElseThrow();
        assertThat(lido.getItens()).isEqualTo(o.getItens());
        assertThat(lido.getValorTotal()).isEqualByComparingTo("131.00");
        assertThat(lido.getStatus()).isEqualTo(StatusOrcamento.PENDENTE);
        assertThat(lido.getOsId()).isEqualTo(1001L);
    }

    @Test
    void atualizaStatusSemDuplicarItens() {
        Orcamento o = novo(1002L);
        orcamentos.salvar(o);

        Orcamento carregado = orcamentos.buscarPorIdParaAtualizar(o.getId()).orElseThrow();
        carregado.registrarPagamentoAprovado(T0.plusSeconds(5));
        orcamentos.salvar(carregado);

        Orcamento lido = orcamentos.buscarPorId(o.getId()).orElseThrow();
        assertThat(lido.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(lido.getItens()).hasSize(2);
    }

    @Test
    void naoPermiteDoisOrcamentosAtivosParaAMesmaOs() {
        orcamentos.salvar(novo(1003L));

        assertThatThrownBy(() -> orcamentos.salvar(novo(1003L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void permiteNovoOrcamentoDepoisDeCancelarOAnterior() {
        Orcamento antigo = novo(1004L);
        orcamentos.salvar(antigo);
        antigo.cancelar("refeito", T0.plusSeconds(1));
        orcamentos.salvar(antigo);

        orcamentos.salvar(novo(1004L));

        assertThat(orcamentos.buscarPorOs(1004L)).hasSize(2);
    }

    @Test
    void naoPermiteDoisPagamentosAprovadosParaOMesmoOrcamento() {
        Orcamento o = novo(1005L);
        orcamentos.salvar(o);
        pagamentos.salvar(Pagamento.aprovado(UUID.randomUUID().toString(), o, "APRO", "ext-1", T0));

        assertThatThrownBy(() -> pagamentos.salvar(
                Pagamento.aprovado(UUID.randomUUID().toString(), o, "APRO", "ext-2", T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pagamentoRecusadoNaoImpedeNovaTentativa() {
        Orcamento o = novo(1006L);
        orcamentos.salvar(o);
        pagamentos.salvar(Pagamento.recusado(UUID.randomUUID().toString(), o, "FUND", "saldo", T0));
        Pagamento aprovado = Pagamento.aprovado(UUID.randomUUID().toString(), o, "APRO", "ext-9", T0.plusSeconds(3));
        pagamentos.salvar(aprovado);

        assertThat(pagamentos.buscarAprovadoDoOrcamento(o.getId())).isPresent();
        assertThat(pagamentos.buscarPorOrcamento(o.getId())).hasSize(2);
    }

    @Test
    void estornoAtualizaOMesmoRegistro() {
        Orcamento o = novo(1007L);
        orcamentos.salvar(o);
        Pagamento p = Pagamento.aprovado(UUID.randomUUID().toString(), o, "APRO", "ext-3", T0);
        pagamentos.salvar(p);

        Pagamento lido = pagamentos.buscarPorId(p.getId()).orElseThrow();
        lido.estornar("falha na execução", T0.plusSeconds(9));
        pagamentos.salvar(lido);

        assertThat(pagamentos.buscarPorId(p.getId()).orElseThrow().getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(pagamentos.buscarAprovadoDoOrcamento(o.getId())).isEmpty();
    }

    @Test
    void linkAguardandoGuardaSagaLinkEValidadeEVenceQuandoPassaDoPrazo() {
        Orcamento o = novo(1010L);
        orcamentos.salvar(o);
        Pagamento p = Pagamento.aguardando(UUID.randomUUID().toString(), o, "LINK", "PREF-1", "https://mp/pagar/1",
                T0.plusSeconds(60), "saga-9", "corr-9", T0);
        pagamentos.salvar(p);

        Pagamento lido = pagamentos.buscarAguardandoDoOrcamento(o.getId()).orElseThrow();
        assertThat(lido.getLinkPagamento()).isEqualTo("https://mp/pagar/1");
        assertThat(lido.getSagaId()).isEqualTo("saga-9");
        assertThat(lido.getCorrelationId()).isEqualTo("corr-9");
        assertThat(lido.getExpiraEm()).isEqualTo(T0.plusSeconds(60));
        assertThat(pagamentos.buscarAguardandoVencidos(T0.plusSeconds(59)))
                .noneMatch(x -> x.getId().equals(p.getId()));
        assertThat(pagamentos.buscarAguardandoVencidos(T0.plusSeconds(60)))
                .anyMatch(x -> x.getId().equals(p.getId()));
        assertThat(pagamentos.buscarPorIdParaAtualizar(p.getId())).isPresent();
    }

    @Test
    void aprovarOLinkTrocaOIdExternoESaiDaListaDeAguardando() {
        Orcamento o = novo(1011L);
        orcamentos.salvar(o);
        Pagamento p = Pagamento.aguardando(UUID.randomUUID().toString(), o, "LINK", "PREF-2", "https://mp/pagar/2",
                T0.plusSeconds(60), "saga-1", "corr-1", T0);
        pagamentos.salvar(p);

        Pagamento lido = pagamentos.buscarPorId(p.getId()).orElseThrow();
        lido.aprovar("MP-55", T0.plusSeconds(5));
        pagamentos.salvar(lido);

        assertThat(pagamentos.buscarAguardandoDoOrcamento(o.getId())).isEmpty();
        assertThat(pagamentos.buscarAprovadoDoOrcamento(o.getId()).orElseThrow().getIdExterno()).isEqualTo("MP-55");
    }

    @Test
    void naoPermiteDoisLinksAguardandoParaOMesmoOrcamento() {
        Orcamento o = novo(1012L);
        orcamentos.salvar(o);
        pagamentos.salvar(Pagamento.aguardando(UUID.randomUUID().toString(), o, "LINK", "P1", "https://mp/1",
                T0.plusSeconds(60), "s", "c", T0));

        assertThatThrownBy(() -> pagamentos.salvar(Pagamento.aguardando(UUID.randomUUID().toString(), o, "LINK",
                "P2", "https://mp/2", T0.plusSeconds(60), "s", "c", T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void registraMensagemProcessadaEReconhece() {
        String id = UUID.randomUUID().toString();
        assertThat(processadas.jaProcessada(id)).isFalse();

        processadas.registrar(id, T0);

        assertThat(processadas.jaProcessada(id)).isTrue();
    }

    @Test
    void mensagemRepetidaViolaAChavePrimaria() {
        String id = UUID.randomUUID().toString();
        processadas.registrar(id, T0);

        assertThatThrownBy(() -> processadas.registrar(id, T0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
