package br.com.oficina.billing.application.service;

import br.com.oficina.billing.application.port.in.ConfirmacaoPagamentos;
import br.com.oficina.billing.application.port.out.EventoSaga;
import br.com.oficina.billing.application.port.out.OrcamentoRepository;
import br.com.oficina.billing.application.port.out.PagamentoGateway;
import br.com.oficina.billing.application.port.out.PagamentoGateway.PagamentoConsultado;
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
 * Fecha o ciclo do pagamento por link. Quem avisa é o provedor (webhook), e o aviso é só um gatilho: o
 * pagamento é sempre consultado no provedor antes de qualquer decisão. Repetições do mesmo aviso são seguras
 * (idempotentes), e pagamento que chega depois do link vencer é devolvido sozinho.
 */
public class ConfirmacaoPagamentoService implements ConfirmacaoPagamentos {

    private static final Logger LOG = LoggerFactory.getLogger(ConfirmacaoPagamentoService.class);

    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;
    private final PublicadorEventos eventos;
    private final PagamentoGateway gateway;
    private final Clock relogio;

    public ConfirmacaoPagamentoService(OrcamentoRepository orcamentos, PagamentoRepository pagamentos,
                                       PublicadorEventos eventos, PagamentoGateway gateway, Clock relogio) {
        this.orcamentos = orcamentos;
        this.pagamentos = pagamentos;
        this.eventos = eventos;
        this.gateway = gateway;
        this.relogio = relogio;
    }

    @Override
    public void pagamentoAtualizado(String idPagamentoExterno) {
        PagamentoConsultado consultado = gateway.consultarPagamento(idPagamentoExterno);
        if (consultado.referencia() == null || consultado.referencia().isBlank()) {
            LOG.warn("Pagamento {} do provedor sem referência do nosso pagamento; ignorado", idPagamentoExterno);
            return;
        }
        Pagamento pagamento = pagamentos.buscarPorIdParaAtualizar(consultado.referencia()).orElse(null);
        if (pagamento == null) {
            LOG.warn("Pagamento {} do provedor aponta para {}, que não existe aqui; ignorado",
                    idPagamentoExterno, consultado.referencia());
            return;
        }
        if (consultado.situacao() != PagamentoConsultado.Situacao.APROVADO) {
            // Recusa ou pendência não encerram o link: o cliente pode tentar de novo até ele vencer
            LOG.info("Pagamento {} do provedor está {}; o link {} continua valendo", idPagamentoExterno,
                    consultado.situacao(), pagamento.getId());
            return;
        }
        aprovar(pagamento, idPagamentoExterno);
    }

    @Override
    public void aprovarSimulado(String pagamentoId) {
        Pagamento pagamento = pagamentos.buscarPorIdParaAtualizar(pagamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento não encontrado: " + pagamentoId));
        aprovar(pagamento, "SIM-" + pagamento.getId());
    }

    private void aprovar(Pagamento pagamento, String idPagamentoExterno) {
        switch (pagamento.getStatus()) {
            case AGUARDANDO -> confirmar(pagamento, idPagamentoExterno);
            case APROVADO, ESTORNADO -> {
                if (idPagamentoExterno.equals(pagamento.getIdExterno())) {
                    LOG.info("Aviso repetido do pagamento {}; nada a fazer", idPagamentoExterno);
                } else { // segundo pagamento no mesmo link: cobrou a mais, devolve
                    devolverPagamentoExtra(pagamento, idPagamentoExterno, "Pagamento em duplicidade");
                }
            }
            case EXPIRADO, RECUSADO -> devolverPagamentoExtra(pagamento, idPagamentoExterno,
                    "Pago após o vencimento do link");
        }
    }

    private void confirmar(Pagamento pagamento, String idPagamentoExterno) {
        Orcamento orcamento = orcamentos.buscarPorIdParaAtualizar(pagamento.getOrcamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Orçamento não encontrado: " + pagamento.getOrcamentoId()));
        if (orcamento.getStatus() != StatusOrcamento.PENDENTE) { // defensivo: cancelar_orçamento já expira o link
            devolverPagamentoExtra(pagamento, idPagamentoExterno, "Orçamento " + orcamento.getStatus());
            return;
        }
        Instant agora = agora();
        pagamento.aprovar(idPagamentoExterno, agora);
        orcamento.registrarPagamentoAprovado(agora);
        pagamentos.salvar(pagamento);
        orcamentos.salvar(orcamento);
        responder(pagamento, "PAGAMENTO_APROVADO",
                mapa("pagamentoId", pagamento.getId(), "orcamentoId", orcamento.getId()));
    }

    /** Dinheiro que entrou sem dever entrar: devolve ao cliente. Se o provedor falhar, o aviso é reenviado. */
    private void devolverPagamentoExtra(Pagamento pagamento, String idPagamentoExterno, String motivo) {
        LOG.warn("{} (pagamento {}, id no provedor {}); devolvendo ao cliente", motivo, pagamento.getId(),
                idPagamentoExterno);
        gateway.estornar(idPagamentoExterno, pagamento.getValor());
        if (pagamento.getStatus() == StatusPagamento.EXPIRADO || pagamento.getStatus() == StatusPagamento.RECUSADO) {
            pagamento.anotar(motivo + "; devolvido automaticamente (" + idPagamentoExterno + ")", agora());
            pagamentos.salvar(pagamento);
        }
    }

    // ------------------------------------------------------------ expiração

    @Override
    public int expirarVencidos() {
        int vencidos = 0;
        for (Pagamento candidato : pagamentos.buscarAguardandoVencidos(agora())) {
            // relê com trava: um webhook de aprovação pode ter chegado agora
            Pagamento pagamento = pagamentos.buscarPorIdParaAtualizar(candidato.getId()).orElse(null);
            if (pagamento != null && pagamento.estaVencido(agora())) {
                expirar(pagamento, "Link de pagamento expirou sem pagamento");
                vencidos++;
            }
        }
        return vencidos;
    }

    @Override
    public void expirarAgora(String pagamentoId) {
        Pagamento pagamento = pagamentos.buscarPorIdParaAtualizar(pagamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento não encontrado: " + pagamentoId));
        if (pagamento.getStatus() != StatusPagamento.AGUARDANDO) {
            throw new RegraDeNegocioException("Pagamento " + pagamentoId + " está " + pagamento.getStatus()
                    + " e não aguarda pagamento");
        }
        expirar(pagamento, "Link de pagamento expirou sem pagamento (simulado)");
    }

    private void expirar(Pagamento pagamento, String motivo) {
        pagamento.expirar(motivo, agora());
        pagamentos.salvar(pagamento);
        // Para a Saga, link vencido é pagamento recusado: roda a compensação A
        responder(pagamento, "PAGAMENTO_RECUSADO", mapa("orcamentoId", pagamento.getOrcamentoId(), "motivo", motivo));
    }

    // ------------------------------------------------------------ apoio

    private void responder(Pagamento pagamento, String tipo, Map<String, Object> payload) {
        eventos.publicar(new EventoSaga(tipo, pagamento.getOsId(), pagamento.getSagaId(),
                pagamento.getCorrelationId(), payload));
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
