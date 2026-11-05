package br.com.oficina.billing.infrastructure.mensageria;

import br.com.oficina.billing.application.port.in.ComandoBilling.CriarOrcamento;
import br.com.oficina.billing.application.port.in.ContextoMensagem;
import br.com.oficina.billing.application.port.in.ProcessadorComandos;
import br.com.oficina.billing.domain.model.ItemOrcamento;
import br.com.oficina.billing.domain.model.TipoItem;
import br.com.oficina.billing.infrastructure.persistence.OutboxEventoEntity;
import br.com.oficina.billing.infrastructure.persistence.OutboxEventoJpa;
import br.com.oficina.billing.infrastructure.persistence.PostgresTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class OutboxRelayTest extends PostgresTestBase {

    private static final AtomicLong OS = new AtomicLong(9000);

    @Autowired ProcessadorComandos processador;
    @Autowired OutboxEventoJpa outbox;
    @Autowired TransactionTemplate tx;
    @Autowired ObjectMapper json;

    /** Guarda o que seria enviado ao SNS; pode ser configurado para falhar. */
    static class PublicadorFake implements PublicadorMensagens {
        final List<String> tipos = new ArrayList<>();
        final List<String> corpos = new ArrayList<>();
        boolean falhar;

        @Override
        public void publicar(String tipo, String envelopeJson) {
            if (falhar) {
                throw new IllegalStateException("SNS indisponível");
            }
            tipos.add(tipo);
            corpos.add(envelopeJson);
        }
    }

    private OutboxRelay relay(PublicadorFake fake) {
        return new OutboxRelay(outbox, tx, json, fake, Clock.systemUTC(), 100);
    }

    private long novoPedido() {
        long os = OS.incrementAndGet();
        processador.processar(new ContextoMensagem(UUID.randomUUID().toString(), "saga-" + os, os, "corr-" + os),
                new CriarOrcamento("52998224725", "ana@x.com",
                        List.of(new ItemOrcamento(TipoItem.SERVICO, "S1", "Revisão", 1, new BigDecimal("200.00"))),
                        new BigDecimal("200.00")));
        return os;
    }

    private List<OutboxEventoEntity> daOs(long os) {
        return outbox.findByOsIdOrderByIdAsc(os);
    }

    @Test
    void publicaOsPendentesComEnvelopeCompletoEMarcaComoEnviados() throws Exception {
        long os = novoPedido();
        var fake = new PublicadorFake();

        relay(fake).publicarPendentes();

        int indice = fake.tipos.indexOf("ORCAMENTO_CRIADO");
        assertThat(indice).isGreaterThanOrEqualTo(0);
        JsonNode env = json.readTree(fake.corpos.stream()
                .filter(c -> c.contains("\"osId\":" + os)).findFirst().orElseThrow());
        assertThat(env.get("type").asText()).isEqualTo("ORCAMENTO_CRIADO");
        assertThat(env.get("sagaId").asText()).isEqualTo("saga-" + os);
        assertThat(env.get("correlationId").asText()).isEqualTo("corr-" + os);
        assertThat(env.get("version").asInt()).isEqualTo(1);
        assertThat(env.get("occurredAt").asText()).isNotBlank();
        assertThat(env.get("messageId").asText()).isEqualTo(daOs(os).get(0).getMessageId());
        assertThat(env.get("payload").get("orcamentoId").asText()).isNotBlank();
        assertThat(daOs(os)).allSatisfy(e -> assertThat(e.getPublicadoEm()).isNotNull());
    }

    @Test
    void naoRepublicaOQueJaFoiEnviado() {
        novoPedido();
        var fake = new PublicadorFake();
        var relay = relay(fake);
        relay.publicarPendentes();
        int antes = fake.tipos.size();

        assertThat(relay.publicarPendentes()).isZero();
        assertThat(fake.tipos).hasSize(antes);
    }

    @Test
    void falhaNoEnvioMantemOEventoPendenteERegistraOErro() {
        // O banco é compartilhado entre os testes: limpa pendências de outros testes, que viriam antes na fila
        relay(new PublicadorFake()).publicarPendentes();
        long os = novoPedido();
        var fake = new PublicadorFake();
        fake.falhar = true;
        var relay = relay(fake);

        relay.publicarPendentes();

        OutboxEventoEntity e = daOs(os).get(0);
        assertThat(e.getPublicadoEm()).isNull();
        assertThat(e.getTentativas()).isEqualTo(1);
        assertThat(e.getUltimoErro()).contains("SNS indisponível");

        fake.falhar = false;
        relay.publicarPendentes();

        OutboxEventoEntity depois = daOs(os).get(0);
        assertThat(depois.getPublicadoEm()).isNotNull();
        assertThat(depois.getUltimoErro()).isNull();
    }

    @Test
    void executarNaoPropagaErros() {
        novoPedido();
        var fake = new PublicadorFake();
        fake.falhar = true;

        assertThatCode(() -> relay(fake).executar()).doesNotThrowAnyException(); // só registra no log
    }
}
