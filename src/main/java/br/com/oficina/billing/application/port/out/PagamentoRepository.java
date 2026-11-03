package br.com.oficina.billing.application.port.out;

import br.com.oficina.billing.domain.model.Pagamento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PagamentoRepository {
    void salvar(Pagamento pagamento);

    Optional<Pagamento> buscarPorId(String id);

    /** Igual a {@link #buscarPorId}, mas trava a linha até o fim da transação (webhooks repetidos em paralelo). */
    Optional<Pagamento> buscarPorIdParaAtualizar(String id);

    Optional<Pagamento> buscarAprovadoDoOrcamento(String orcamentoId);

    Optional<Pagamento> buscarAguardandoDoOrcamento(String orcamentoId);

    /** Links ainda não pagos cuja validade já passou. */
    List<Pagamento> buscarAguardandoVencidos(Instant agora);

    /** Do mais novo para o mais antigo. */
    List<Pagamento> buscarPorOrcamento(String orcamentoId);
}
