package br.com.oficina.billing.application.port.out;

import br.com.oficina.billing.domain.model.Pagamento;

import java.util.List;
import java.util.Optional;

public interface PagamentoRepository {
    void salvar(Pagamento pagamento);

    Optional<Pagamento> buscarPorId(String id);

    Optional<Pagamento> buscarAprovadoDoOrcamento(String orcamentoId);

    /** Do mais novo para o mais antigo. */
    List<Pagamento> buscarPorOrcamento(String orcamentoId);
}
