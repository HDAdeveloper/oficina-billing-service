package br.com.oficina.billing.application.port.in;

import br.com.oficina.billing.domain.model.Orcamento;
import br.com.oficina.billing.domain.model.Pagamento;

import java.util.List;

/** Porta de entrada da API REST (somente leitura). */
public interface ConsultaOrcamentos {

    Orcamento buscarPorId(String orcamentoId);

    /** Todos os orçamentos da OS, do mais novo para o mais antigo. */
    List<Orcamento> listarPorOs(Long osId);

    /** Tentativas de pagamento do orçamento, da mais nova para a mais antiga. */
    List<Pagamento> listarPagamentos(String orcamentoId);
}
