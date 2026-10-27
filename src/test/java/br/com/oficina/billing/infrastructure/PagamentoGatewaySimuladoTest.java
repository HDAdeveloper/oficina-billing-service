package br.com.oficina.billing.infrastructure;

import br.com.oficina.billing.application.port.out.GatewayPagamentoIndisponivelException;
import br.com.oficina.billing.application.port.out.PagamentoGateway.ResultadoCobranca;
import br.com.oficina.billing.application.port.out.PagamentoGateway.SolicitacaoCobranca;
import br.com.oficina.billing.infrastructure.gateway.PagamentoGatewaySimulado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagamentoGatewaySimuladoTest {

    private final PagamentoGatewaySimulado gateway = new PagamentoGatewaySimulado();

    private static SolicitacaoCobranca cobranca(String metodo) {
        return new SolicitacaoCobranca("pag-1", "orc-1", 42L, new BigDecimal("100.00"), metodo, "a@x.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"APRO", "apro", "PIX", "CARTAO"})
    void aprovaPorPadraoEParaMetodosComuns(String metodo) {
        assertThat(gateway.cobrar(cobranca(metodo))).isInstanceOf(ResultadoCobranca.Aprovada.class);
    }

    @Test
    void geraIdExternoUnicoPorCobranca() {
        var a = (ResultadoCobranca.Aprovada) gateway.cobrar(cobranca("APRO"));
        var b = (ResultadoCobranca.Aprovada) gateway.cobrar(cobranca("APRO"));
        assertThat(a.idExterno()).startsWith("SIM-APRO-").isNotEqualTo(b.idExterno());
    }

    @Test
    void recusaPorSaldoOuEmissor() {
        assertThat(gateway.cobrar(cobranca("FUND"))).isEqualTo(new ResultadoCobranca.Recusada("Saldo insuficiente"));
        assertThat(gateway.cobrar(cobranca(" othe ")))
                .isEqualTo(new ResultadoCobranca.Recusada("Pagamento recusado pelo emissor"));
    }

    @Test
    void provedorIndisponivelLancaExcecao() {
        assertThatThrownBy(() -> gateway.cobrar(cobranca("ERRO")))
                .isInstanceOf(GatewayPagamentoIndisponivelException.class);
    }

    @Test
    void estornoFalhaSoQuandoSimulado() {
        gateway.estornar("SIM-APRO-1", BigDecimal.TEN);
        gateway.estornar(null, BigDecimal.TEN);
        assertThatThrownBy(() -> gateway.estornar("SIM-ERRO_ESTORNO-1", BigDecimal.TEN))
                .isInstanceOf(GatewayPagamentoIndisponivelException.class);
    }
}
