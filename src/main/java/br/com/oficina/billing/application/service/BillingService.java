package br.com.oficina.billing.application.service;

import br.com.oficina.billing.application.port.in.ComandoBilling;
import br.com.oficina.billing.application.port.in.ComandoBilling.CancelarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.CobrarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.CriarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.EstornarPagamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.OrcamentoInvalido;
import br.com.oficina.billing.application.port.in.ContextoMensagem;
import br.com.oficina.billing.application.port.in.ProcessadorComandos;
import br.com.oficina.billing.application.port.out.EventoSaga;
import br.com.oficina.billing.application.port.out.GeradorId;
import br.com.oficina.billing.application.port.out.MensagemProcessadaRepository;
import br.com.oficina.billing.application.port.out.OrcamentoRepository;
import br.com.oficina.billing.application.port.out.PagamentoGateway;
import br.com.oficina.billing.application.port.out.PagamentoGateway.ResultadoCobranca;
import br.com.oficina.billing.application.port.out.PagamentoRepository;
import br.com.oficina.billing.application.port.out.PublicadorEventos;
import br.com.oficina.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.domain.model.Orcamento;
import br.com.oficina.billing.domain.model.Pagamento;
import br.com.oficina.billing.domain.model.StatusOrcamento;
import br.com.oficina.billing.domain.model.StatusPagamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trata os comandos da Saga. Duas camadas de idempotência, porque a entrega é at-least-once:
 * <ol>
 *   <li>pelo {@code messageId}: a mesma mensagem entregue de novo é ignorada;</li>
 *   <li>pela regra de negócio: o mesmo pedido enviado em mensagens diferentes (duplo clique) gera o
 *       mesmo resultado, sem criar dois orçamentos nem cobrar duas vezes.</li>
 * </ol>
 */
public class BillingService implements ProcessadorComandos {

    private static final Logger LOG = LoggerFactory.getLogger(BillingService.class);

    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;
    private final MensagemProcessadaRepository processadas;
    private final PublicadorEventos eventos;
    private final PagamentoGateway gateway;
    private final GeradorId ids;
    private final Clock relogio;

    public BillingService(OrcamentoRepository orcamentos, PagamentoRepository pagamentos,
                          MensagemProcessadaRepository processadas, PublicadorEventos eventos,
                          PagamentoGateway gateway, GeradorId ids, Clock relogio) {
        this.orcamentos = orcamentos;
        this.pagamentos = pagamentos;
        this.processadas = processadas;
        this.eventos = eventos;
        this.gateway = gateway;
        this.ids = ids;
        this.relogio = relogio;
    }

    @Override
    public void processar(ContextoMensagem ctx, ComandoBilling comando) {
        if (processadas.jaProcessada(ctx.messageId())) {
            LOG.info("Mensagem {} já processada; ignorando (entrega repetida)", ctx.messageId());
            return;
        }
        switch (comando) {
            case CriarOrcamento c -> criarOrcamento(ctx, c);
            case CobrarOrcamento c -> cobrarOrcamento(ctx, c);
            case CancelarOrcamento c -> cancelarOrcamento(ctx, c);
            case EstornarPagamento c -> estornarPagamento(ctx, c);
            case OrcamentoInvalido c -> {
                LOG.warn("Orçamento da OS {} recusado: {}", ctx.osId(), c.motivo());
                responder(ctx, "ORCAMENTO_FALHOU", mapa("motivo", c.motivo()));
            }
        }
        processadas.registrar(ctx.messageId(), agora());
    }

    // ------------------------------------------------------------ CRIAR_ORCAMENTO

    private void criarOrcamento(ContextoMensagem ctx, CriarOrcamento cmd) {
        Orcamento orcamento;
        try {
            orcamento = obterOuCriar(ctx, cmd);
        } catch (RegraDeNegocioException e) {
            // Dado inválido nunca passa na próxima tentativa: responde com falha em vez de repetir
            LOG.warn("Orçamento da OS {} recusado: {}", ctx.osId(), e.getMessage());
            responder(ctx, "ORCAMENTO_FALHOU", mapa("motivo", e.getMessage()));
            return;
        }
        responder(ctx, "ORCAMENTO_CRIADO",
                mapa("orcamentoId", orcamento.getId(), "valorTotal", orcamento.getValorTotal()));
    }

    private Orcamento obterOuCriar(ContextoMensagem ctx, CriarOrcamento cmd) {
        if (ctx.osId() == null) {
            throw new RegraDeNegocioException("OS não informada");
        }
        if (cmd.itens() == null || cmd.itens().isEmpty()) {
            throw new RegraDeNegocioException("O orçamento precisa ter ao menos um item");
        }
        Orcamento novo = Orcamento.criar(ids.novo(), ctx.osId(), cmd.clienteId(), cmd.emailCliente(),
                cmd.itens(), agora());
        if (cmd.valorTotal() != null && novo.getValorTotal().compareTo(cmd.valorTotal()) != 0) {
            throw new RegraDeNegocioException("Valor total divergente: informado " + cmd.valorTotal()
                    + ", calculado " + novo.getValorTotal());
        }

        for (Orcamento existente : orcamentos.buscarPorOs(ctx.osId())) {
            if (!existente.estaAtivo()) {
                continue;
            }
            if (existente.getStatus() == StatusOrcamento.PAGO) {
                throw new RegraDeNegocioException("A OS já tem um orçamento pago: " + existente.getId());
            }
            if (existente.temOsMesmosItens(novo.getItens())) {
                return existente; // pedido repetido: devolve o mesmo orçamento
            }
            // Itens mudaram desde o pedido anterior: o antigo perde a validade
            existente.cancelar("Substituído por novo orçamento", agora());
            orcamentos.salvar(existente);
        }
        orcamentos.salvar(novo);
        return novo;
    }

    // ------------------------------------------------------------ COBRAR_ORCAMENTO

    private void cobrarOrcamento(ContextoMensagem ctx, CobrarOrcamento cmd) {
        Orcamento orcamento = orcamentos.buscarPorIdParaAtualizar(cmd.orcamentoId()).orElse(null);
        if (orcamento == null) {
            recusar(ctx, cmd.orcamentoId(), "Orçamento não encontrado");
            return;
        }

        Pagamento jaAprovado = pagamentos.buscarAprovadoDoOrcamento(orcamento.getId()).orElse(null);
        if (jaAprovado != null) { // cobrança repetida: não cobra de novo
            responder(ctx, "PAGAMENTO_APROVADO",
                    mapa("pagamentoId", jaAprovado.getId(), "orcamentoId", orcamento.getId()));
            return;
        }
        if (orcamento.getStatus() != StatusOrcamento.PENDENTE) {
            recusar(ctx, orcamento.getId(), "Orçamento não está pendente (" + orcamento.getStatus() + ")");
            return;
        }
        if (cmd.valorTotal() != null && orcamento.getValorTotal().compareTo(cmd.valorTotal()) != 0) {
            recusar(ctx, orcamento.getId(), "Valor a cobrar diverge do orçamento: informado "
                    + cmd.valorTotal() + ", orçamento " + orcamento.getValorTotal());
            return;
        }

        String pagamentoId = ids.novo();
        ResultadoCobranca resultado = gateway.cobrar(new PagamentoGateway.SolicitacaoCobranca(
                pagamentoId, orcamento.getId(), orcamento.getOsId(), orcamento.getValorTotal(),
                cmd.metodo(), orcamento.getEmailCliente()));

        switch (resultado) {
            case ResultadoCobranca.Aprovada ok -> {
                Pagamento pagamento = Pagamento.aprovado(pagamentoId, orcamento, cmd.metodo(), ok.idExterno(), agora());
                orcamento.registrarPagamentoAprovado(agora());
                pagamentos.salvar(pagamento);
                orcamentos.salvar(orcamento);
                responder(ctx, "PAGAMENTO_APROVADO",
                        mapa("pagamentoId", pagamento.getId(), "orcamentoId", orcamento.getId()));
            }
            case ResultadoCobranca.Recusada nok -> {
                pagamentos.salvar(Pagamento.recusado(pagamentoId, orcamento, cmd.metodo(), nok.motivo(), agora()));
                recusar(ctx, orcamento.getId(), nok.motivo());
            }
        }
    }

    private void recusar(ContextoMensagem ctx, String orcamentoId, String motivo) {
        responder(ctx, "PAGAMENTO_RECUSADO", mapa("orcamentoId", orcamentoId, "motivo", motivo));
    }

    // ------------------------------------------------------------ CANCELAR_ORCAMENTO

    private void cancelarOrcamento(ContextoMensagem ctx, CancelarOrcamento cmd) {
        Orcamento orcamento = orcamentos.buscarPorIdParaAtualizar(cmd.orcamentoId()).orElse(null);
        if (orcamento == null) {
            // Nada a desfazer: a compensação já está satisfeita
            LOG.warn("CANCELAR_ORCAMENTO para orçamento inexistente {}; respondendo como cancelado", cmd.orcamentoId());
            responder(ctx, "ORCAMENTO_CANCELADO", mapa("orcamentoId", cmd.orcamentoId()));
            return;
        }
        try {
            if (orcamento.cancelar(cmd.motivo(), agora())) {
                orcamentos.salvar(orcamento);
            }
        } catch (RegraDeNegocioException e) {
            // Orçamento pago/estornado não se cancela: é erro do orquestrador, repetir não resolve
            LOG.error("CANCELAR_ORCAMENTO rejeitado para o orçamento {}: {}", cmd.orcamentoId(), e.getMessage());
            return;
        }
        responder(ctx, "ORCAMENTO_CANCELADO", mapa("orcamentoId", orcamento.getId()));
    }

    // ------------------------------------------------------------ ESTORNAR_PAGAMENTO

    private void estornarPagamento(ContextoMensagem ctx, EstornarPagamento cmd) {
        Pagamento pagamento = pagamentos.buscarPorId(cmd.pagamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento não encontrado: " + cmd.pagamentoId()));

        if (pagamento.getStatus() == StatusPagamento.ESTORNADO) { // comando repetido
            responder(ctx, "PAGAMENTO_ESTORNADO", mapa("pagamentoId", pagamento.getId()));
            return;
        }
        if (pagamento.getStatus() != StatusPagamento.APROVADO) {
            throw new RegraDeNegocioException("Pagamento " + pagamento.getId() + " está "
                    + pagamento.getStatus() + " e não pode ser estornado");
        }
        Orcamento orcamento = orcamentos.buscarPorIdParaAtualizar(pagamento.getOrcamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Orçamento não encontrado: " + pagamento.getOrcamentoId()));

        // Se o provedor falhar, a exceção desfaz a transação e a mensagem é tentada de novo (depois, DLQ)
        gateway.estornar(pagamento.getIdExterno(), pagamento.getValor());

        pagamento.estornar(cmd.motivo(), agora());
        orcamento.estornar(agora());
        pagamentos.salvar(pagamento);
        orcamentos.salvar(orcamento);
        responder(ctx, "PAGAMENTO_ESTORNADO", mapa("pagamentoId", pagamento.getId()));
    }

    // ------------------------------------------------------------ apoio

    private void responder(ContextoMensagem ctx, String tipo, Map<String, Object> payload) {
        eventos.publicar(new EventoSaga(tipo, ctx.osId(), ctx.sagaId(), ctx.correlationId(), payload));
    }

    private static Map<String, Object> mapa(Object... paresChaveValor) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            m.put((String) paresChaveValor[i], paresChaveValor[i + 1]);
        }
        return m;
    }

    private Instant agora() {
        return Instant.now(relogio);
    }
}
