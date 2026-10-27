package br.com.oficina.billing.application.service;

import br.com.oficina.billing.application.port.in.ConsultaOrcamentos;
import br.com.oficina.billing.application.port.out.OrcamentoRepository;
import br.com.oficina.billing.application.port.out.PagamentoRepository;
import br.com.oficina.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.domain.model.Orcamento;
import br.com.oficina.billing.domain.model.Pagamento;

import java.util.List;

public class ConsultaOrcamentosService implements ConsultaOrcamentos {

    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;

    public ConsultaOrcamentosService(OrcamentoRepository orcamentos, PagamentoRepository pagamentos) {
        this.orcamentos = orcamentos;
        this.pagamentos = pagamentos;
    }

    @Override
    public Orcamento buscarPorId(String orcamentoId) {
        return orcamentos.buscarPorId(orcamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orçamento não encontrado: " + orcamentoId));
    }

    @Override
    public List<Orcamento> listarPorOs(Long osId) {
        return orcamentos.buscarPorOs(osId);
    }

    @Override
    public List<Pagamento> listarPagamentos(String orcamentoId) {
        buscarPorId(orcamentoId); // 404 se o orçamento não existe
        return pagamentos.buscarPorOrcamento(orcamentoId);
    }
}
