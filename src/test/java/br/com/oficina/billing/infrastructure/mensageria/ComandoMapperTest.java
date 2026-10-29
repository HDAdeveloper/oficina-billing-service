package br.com.oficina.billing.infrastructure.mensageria;

import br.com.oficina.billing.application.port.in.ComandoBilling.CancelarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.CobrarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.CriarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.EstornarPagamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.OrcamentoInvalido;
import br.com.oficina.billing.domain.model.TipoItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComandoMapperTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ComandoMapper mapper = new ComandoMapper();

    private EnvelopeMensagem env(String type, String payload) throws Exception {
        JsonNode p = payload == null ? null : json.readTree(payload);
        return new EnvelopeMensagem("m-1", "s-1", 42L, type, 1, "2026-11-03T14:05:00Z", "corr-1", p);
    }

    @Test
    void mapeiaCriarOrcamentoComItensEContexto() throws Exception {
        var r = mapper.mapear(env("CRIAR_ORCAMENTO", """
                {"clienteId":"52998224725","emailCliente":"ana@x.com","valorTotal":131.00,"itens":[
                  {"tipo":"SERVICO","codigo":"S1","descricao":"Troca de oleo","quantidade":1,"precoUnitario":80},
                  {"tipo":"peca","codigo":"P1","descricao":"Filtro","quantidade":2,"precoUnitario":"25.50"}]}"""));

        assertThat(r.contexto().messageId()).isEqualTo("m-1");
        assertThat(r.contexto().sagaId()).isEqualTo("s-1");
        assertThat(r.contexto().osId()).isEqualTo(42L);
        assertThat(r.contexto().correlationId()).isEqualTo("corr-1");
        var c = (CriarOrcamento) r.comando();
        assertThat(c.clienteId()).isEqualTo("52998224725");
        assertThat(c.emailCliente()).isEqualTo("ana@x.com");
        assertThat(c.valorTotal()).isEqualByComparingTo("131.00");
        assertThat(c.itens()).hasSize(2);
        assertThat(c.itens().get(1).tipo()).isEqualTo(TipoItem.PECA);
        assertThat(c.itens().get(1).precoUnitario()).isEqualByComparingTo("25.50");
    }

    @Test
    void itemInvalidoViraOrcamentoInvalidoComOMotivo() throws Exception {
        var r = mapper.mapear(env("CRIAR_ORCAMENTO", """
                {"itens":[{"tipo":"SERVICO","codigo":"S1","descricao":"X","quantidade":0,"precoUnitario":10}]}"""));

        assertThat(r.comando()).isInstanceOf(OrcamentoInvalido.class);
        assertThat(((OrcamentoInvalido) r.comando()).motivo()).contains("Quantidade");
    }

    @Test
    void tipoDeItemDesconhecidoViraOrcamentoInvalido() throws Exception {
        var r = mapper.mapear(env("CRIAR_ORCAMENTO", """
                {"itens":[{"tipo":"BRINDE","codigo":"S1","descricao":"X","quantidade":1,"precoUnitario":10}]}"""));

        assertThat(((OrcamentoInvalido) r.comando()).motivo()).contains("BRINDE");
    }

    @Test
    void tipoDeItemAusenteViraOrcamentoInvalido() throws Exception {
        var r = mapper.mapear(env("CRIAR_ORCAMENTO", """
                {"itens":[{"codigo":"S1","descricao":"X","quantidade":1,"precoUnitario":10}]}"""));

        assertThat(((OrcamentoInvalido) r.comando()).motivo()).contains("Tipo do item");
    }

    @Test
    void numeroInvalidoViraOrcamentoInvalido() throws Exception {
        var r = mapper.mapear(env("CRIAR_ORCAMENTO", """
                {"itens":[{"tipo":"SERVICO","codigo":"S1","descricao":"X","quantidade":1,"precoUnitario":"abc"}]}"""));

        assertThat(((OrcamentoInvalido) r.comando()).motivo()).contains("numérico");
    }

    @Test
    void criarSemItensSegueParaOServicoDecidir() throws Exception {
        var r = mapper.mapear(env("CRIAR_ORCAMENTO", "{}"));
        assertThat(((CriarOrcamento) r.comando()).itens()).isEmpty();

        var semPayload = mapper.mapear(env("CRIAR_ORCAMENTO", null));
        assertThat(((CriarOrcamento) semPayload.comando()).itens()).isEmpty();
    }

    @Test
    void mapeiaCobrarCancelarEEstornar() throws Exception {
        var cobrar = (CobrarOrcamento) mapper.mapear(env("COBRAR_ORCAMENTO",
                "{\"orcamentoId\":\"o-1\",\"valorTotal\":200.00,\"metodo\":\"FUND\"}")).comando();
        assertThat(cobrar.orcamentoId()).isEqualTo("o-1");
        assertThat(cobrar.valorTotal()).isEqualByComparingTo("200");
        assertThat(cobrar.metodo()).isEqualTo("FUND");

        var semValor = (CobrarOrcamento) mapper.mapear(env("COBRAR_ORCAMENTO", "{\"orcamentoId\":\"o-1\"}")).comando();
        assertThat(semValor.valorTotal()).isNull();
        assertThat(semValor.metodo()).isNull();

        var cancelar = (CancelarOrcamento) mapper.mapear(env("CANCELAR_ORCAMENTO",
                "{\"orcamentoId\":\"o-1\",\"motivo\":\"cliente recusou\"}")).comando();
        assertThat(cancelar.motivo()).isEqualTo("cliente recusou");

        var estornar = (EstornarPagamento) mapper.mapear(env("ESTORNAR_PAGAMENTO",
                "{\"pagamentoId\":\"p-1\",\"motivo\":\"falha\"}")).comando();
        assertThat(estornar.pagamentoId()).isEqualTo("p-1");
    }

    @Test
    void camposObrigatoriosAusentesSaoMensagemInvalida() {
        assertThatThrownBy(() -> mapper.mapear(env("COBRAR_ORCAMENTO", "{}")))
                .isInstanceOf(MensagemInvalidaException.class).hasMessageContaining("orcamentoId");
        assertThatThrownBy(() -> mapper.mapear(env("ESTORNAR_PAGAMENTO", null)))
                .isInstanceOf(MensagemInvalidaException.class).hasMessageContaining("pagamentoId");
    }

    @Test
    void tipoDesconhecidoOuAusenteESemMessageIdSaoInvalidos() {
        assertThatThrownBy(() -> mapper.mapear(env("RESERVAR_ESTOQUE", "{}")))
                .isInstanceOf(MensagemInvalidaException.class).hasMessageContaining("RESERVAR_ESTOQUE");
        assertThatThrownBy(() -> mapper.mapear(null)).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> mapper.mapear(new EnvelopeMensagem("m", "s", 1L, " ", 1, null, null, null)))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> mapper.mapear(new EnvelopeMensagem(null, "s", 1L, "CRIAR_ORCAMENTO", 1, null, null, null)))
                .isInstanceOf(MensagemInvalidaException.class).hasMessageContaining("messageId");
    }
}
