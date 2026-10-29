package br.com.oficina.billing.domain.model;

import br.com.oficina.billing.domain.exception.RegraDeNegocioException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orçamento de uma OS. Transições permitidas:
 * PENDENTE → PAGO → ESTORNADO e PENDENTE → CANCELADO. Os métodos recebem o instante atual
 * para os testes serem determinísticos.
 */
public class Orcamento {

    private final String id;
    private final Long osId;
    private final String clienteId;
    private final String emailCliente;
    private final List<ItemOrcamento> itens;
    private final BigDecimal valorTotal;
    private StatusOrcamento status;
    private String motivoCancelamento;
    private final Instant criadoEm;
    private Instant atualizadoEm;

    private Orcamento(String id, Long osId, String clienteId, String emailCliente, List<ItemOrcamento> itens,
                      StatusOrcamento status, String motivoCancelamento, Instant criadoEm, Instant atualizadoEm) {
        this.id = id;
        this.osId = osId;
        this.clienteId = clienteId;
        this.emailCliente = emailCliente;
        this.itens = new ArrayList<>(itens);
        this.valorTotal = this.itens.stream().map(ItemOrcamento::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        this.status = status;
        this.motivoCancelamento = motivoCancelamento;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static Orcamento criar(String id, Long osId, String clienteId, String emailCliente,
                                  List<ItemOrcamento> itens, Instant agora) {
        if (id == null || id.isBlank()) {
            throw new RegraDeNegocioException("Id do orçamento é obrigatório");
        }
        if (osId == null) {
            throw new RegraDeNegocioException("OS do orçamento é obrigatória");
        }
        if (itens == null || itens.isEmpty()) {
            throw new RegraDeNegocioException("O orçamento precisa ter ao menos um item");
        }
        return new Orcamento(id, osId, clienteId, emailCliente, itens, StatusOrcamento.PENDENTE, null, agora, agora);
    }

    /** Reconstrói um orçamento já gravado (usado pela persistência). */
    public static Orcamento reconstituir(String id, Long osId, String clienteId, String emailCliente,
                                         List<ItemOrcamento> itens, StatusOrcamento status,
                                         String motivoCancelamento, Instant criadoEm, Instant atualizadoEm) {
        return new Orcamento(id, osId, clienteId, emailCliente, itens, status, motivoCancelamento,
                criadoEm, atualizadoEm);
    }

    // ------------------------------------------------------------ transições

    public void registrarPagamentoAprovado(Instant agora) {
        exigirStatus(StatusOrcamento.PENDENTE, "receber pagamento");
        mudar(StatusOrcamento.PAGO, agora);
    }

    /**
     * Cancela um orçamento pendente. Se já estiver cancelado, não faz nada e devolve {@code false}
     * (o comando pode chegar duas vezes). Orçamento pago não pode ser cancelado: precisa ser estornado.
     */
    public boolean cancelar(String motivo, Instant agora) {
        if (status == StatusOrcamento.CANCELADO) {
            return false;
        }
        if (status != StatusOrcamento.PENDENTE) {
            throw new RegraDeNegocioException("Orçamento " + status + " não pode ser cancelado"
                    + (status == StatusOrcamento.PAGO ? "; estorne o pagamento" : ""));
        }
        this.motivoCancelamento = motivo;
        mudar(StatusOrcamento.CANCELADO, agora);
        return true;
    }

    public void estornar(Instant agora) {
        exigirStatus(StatusOrcamento.PAGO, "ser estornado");
        mudar(StatusOrcamento.ESTORNADO, agora);
    }

    private void exigirStatus(StatusOrcamento esperado, String acao) {
        if (status != esperado) {
            throw new RegraDeNegocioException("Orçamento " + id + " está " + status + " e não pode " + acao);
        }
    }

    private void mudar(StatusOrcamento novo, Instant agora) {
        this.status = novo;
        this.atualizadoEm = agora;
    }

    // ------------------------------------------------------------ leitura

    /** Mesmos itens e mesmo valor? Usado para reaproveitar um orçamento pendente igual. */
    public boolean temOsMesmosItens(List<ItemOrcamento> outros) {
        return itens.equals(outros);
    }

    public boolean estaAtivo() {
        return status == StatusOrcamento.PENDENTE || status == StatusOrcamento.PAGO;
    }

    public String getId() { return id; }
    public Long getOsId() { return osId; }
    public String getClienteId() { return clienteId; }
    public String getEmailCliente() { return emailCliente; }
    public List<ItemOrcamento> getItens() { return Collections.unmodifiableList(itens); }
    public BigDecimal getValorTotal() { return valorTotal; }
    public StatusOrcamento getStatus() { return status; }
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
}
