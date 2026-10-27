package br.com.oficina.billing.application.port.out;

import br.com.oficina.billing.domain.model.Orcamento;

import java.util.List;
import java.util.Optional;

public interface OrcamentoRepository {
    void salvar(Orcamento orcamento);

    Optional<Orcamento> buscarPorId(String id);

    /**
     * Busca bloqueando o registro até o fim da transação. Usado ao cobrar, cancelar e estornar, para
     * dois comandos simultâneos sobre o mesmo orçamento não passarem juntos pela mesma verificação.
     */
    Optional<Orcamento> buscarPorIdParaAtualizar(String id);

    /** Do mais novo para o mais antigo. */
    List<Orcamento> buscarPorOs(Long osId);
}
