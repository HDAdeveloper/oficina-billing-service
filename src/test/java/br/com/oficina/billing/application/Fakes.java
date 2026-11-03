package br.com.oficina.billing.application;

import br.com.oficina.billing.application.port.out.EventoSaga;
import br.com.oficina.billing.application.port.out.GeradorId;
import br.com.oficina.billing.application.port.out.MensagemProcessadaRepository;
import br.com.oficina.billing.application.port.out.GatewayPagamentoIndisponivelException;
import br.com.oficina.billing.application.port.out.OrcamentoRepository;
import br.com.oficina.billing.application.port.out.PagamentoGateway;
import br.com.oficina.billing.application.port.out.PagamentoRepository;
import br.com.oficina.billing.application.port.out.PublicadorEventos;
import br.com.oficina.billing.domain.model.Orcamento;
import br.com.oficina.billing.domain.model.Pagamento;
import br.com.oficina.billing.domain.model.StatusPagamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Implementações em memória das portas de saída, para os testes dos casos de uso. */
final class Fakes {

    private Fakes() {
    }

    static class OrcamentosMem implements OrcamentoRepository {
        final Map<String, Orcamento> dados = new LinkedHashMap<>();

        @Override
        public void salvar(Orcamento o) {
            dados.put(o.getId(), o);
        }

        @Override
        public Optional<Orcamento> buscarPorId(String id) {
            return Optional.ofNullable(dados.get(id));
        }

        @Override
        public Optional<Orcamento> buscarPorIdParaAtualizar(String id) {
            return buscarPorId(id);
        }

        @Override
        public List<Orcamento> buscarPorOs(Long osId) {
            return dados.values().stream().filter(o -> o.getOsId().equals(osId))
                    .sorted(Comparator.comparing(Orcamento::getCriadoEm).reversed()).toList();
        }
    }

    static class PagamentosMem implements PagamentoRepository {
        final Map<String, Pagamento> dados = new LinkedHashMap<>();

        @Override
        public void salvar(Pagamento p) {
            dados.put(p.getId(), p);
        }

        @Override
        public Optional<Pagamento> buscarPorId(String id) {
            return Optional.ofNullable(dados.get(id));
        }

        @Override
        public Optional<Pagamento> buscarPorIdParaAtualizar(String id) {
            return buscarPorId(id);
        }

        @Override
        public Optional<Pagamento> buscarAguardandoDoOrcamento(String orcamentoId) {
            return dados.values().stream()
                    .filter(p -> p.getOrcamentoId().equals(orcamentoId) && p.getStatus() == StatusPagamento.AGUARDANDO)
                    .findFirst();
        }

        @Override
        public List<Pagamento> buscarAguardandoVencidos(Instant agora) {
            return dados.values().stream().filter(p -> p.estaVencido(agora)).toList();
        }

        @Override
        public Optional<Pagamento> buscarAprovadoDoOrcamento(String orcamentoId) {
            return dados.values().stream()
                    .filter(p -> p.getOrcamentoId().equals(orcamentoId) && p.getStatus() == StatusPagamento.APROVADO)
                    .findFirst();
        }

        @Override
        public List<Pagamento> buscarPorOrcamento(String orcamentoId) {
            return dados.values().stream().filter(p -> p.getOrcamentoId().equals(orcamentoId))
                    .sorted(Comparator.comparing(Pagamento::getCriadoEm).reversed()).toList();
        }
    }

    static class ProcessadasMem implements MensagemProcessadaRepository {
        final Set<String> ids = new HashSet<>();

        @Override
        public boolean jaProcessada(String messageId) {
            return ids.contains(messageId);
        }

        @Override
        public void registrar(String messageId, Instant quando) {
            ids.add(messageId);
        }
    }

    static class EventosGravador implements PublicadorEventos {
        final List<EventoSaga> publicados = new ArrayList<>();

        @Override
        public void publicar(EventoSaga evento) {
            publicados.add(evento);
        }

        List<String> tipos() {
            return publicados.stream().map(EventoSaga::type).toList();
        }

        EventoSaga ultimo() {
            return publicados.get(publicados.size() - 1);
        }
    }

    static class IdsSequenciais implements GeradorId {
        private int n;

        @Override
        public String novo() {
            return "id-" + (++n);
        }
    }

    /** Provedor de pagamento controlável pelo teste. */
    static class GatewayFake implements PagamentoGateway {
        ResultadoCobranca proximoResultado = new ResultadoCobranca.Aprovada("EXT-1");
        boolean indisponivelNaCobranca;
        boolean indisponivelNoEstorno;
        int cobrancas;
        final List<String> estornados = new ArrayList<>();
        /** O que o provedor "diz" de cada pagamento consultado (id externo → situação). */
        final Map<String, PagamentoConsultado> consultas = new LinkedHashMap<>();

        @Override
        public ResultadoCobranca cobrar(SolicitacaoCobranca solicitacao) {
            if (indisponivelNaCobranca) {
                throw new GatewayPagamentoIndisponivelException("fora do ar");
            }
            cobrancas++;
            return proximoResultado;
        }

        @Override
        public PagamentoConsultado consultarPagamento(String idExterno) {
            PagamentoConsultado c = consultas.get(idExterno);
            if (c == null) {
                throw new GatewayPagamentoIndisponivelException("pagamento desconhecido no fake: " + idExterno);
            }
            return c;
        }

        @Override
        public void estornar(String idExterno, BigDecimal valor) {
            if (indisponivelNoEstorno) {
                throw new GatewayPagamentoIndisponivelException("fora do ar");
            }
            estornados.add(idExterno);
        }
    }
}
