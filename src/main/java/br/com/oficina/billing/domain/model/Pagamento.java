package br.com.oficina.billing.domain.model;

import br.com.oficina.billing.domain.exception.RegraDeNegocioException;

import java.math.BigDecimal;
import java.time.Instant;

/** Tentativa de cobrança de um orçamento. Uma recusa também fica registrada, para auditoria. */
public class Pagamento {

    private final String id;
    private final String orcamentoId;
    private final Long osId;
    private final BigDecimal valor;
    private final String metodo;
    private StatusPagamento status;
    private final String idExterno;
    private String motivo;
    private final Instant criadoEm;
    private Instant atualizadoEm;

    private Pagamento(String id, String orcamentoId, Long osId, BigDecimal valor, String metodo,
                      StatusPagamento status, String idExterno, String motivo, Instant criadoEm,
                      Instant atualizadoEm) {
        this.id = id;
        this.orcamentoId = orcamentoId;
        this.osId = osId;
        this.valor = valor;
        this.metodo = metodo;
        this.status = status;
        this.idExterno = idExterno;
        this.motivo = motivo;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static Pagamento aprovado(String id, Orcamento orcamento, String metodo, String idExterno,
                                     Instant agora) {
        return new Pagamento(id, orcamento.getId(), orcamento.getOsId(), orcamento.getValorTotal(), metodo,
                StatusPagamento.APROVADO, idExterno, null, agora, agora);
    }

    public static Pagamento recusado(String id, Orcamento orcamento, String metodo, String motivo,
                                     Instant agora) {
        return new Pagamento(id, orcamento.getId(), orcamento.getOsId(), orcamento.getValorTotal(), metodo,
                StatusPagamento.RECUSADO, null, motivo, agora, agora);
    }

    public static Pagamento reconstituir(String id, String orcamentoId, Long osId, BigDecimal valor,
                                         String metodo, StatusPagamento status, String idExterno,
                                         String motivo, Instant criadoEm, Instant atualizadoEm) {
        return new Pagamento(id, orcamentoId, osId, valor, metodo, status, idExterno, motivo, criadoEm,
                atualizadoEm);
    }

    /** Devolve o valor. Só um pagamento aprovado pode ser estornado. */
    public void estornar(String motivoEstorno, Instant agora) {
        if (status != StatusPagamento.APROVADO) {
            throw new RegraDeNegocioException("Pagamento " + id + " está " + status + " e não pode ser estornado");
        }
        this.status = StatusPagamento.ESTORNADO;
        this.motivo = motivoEstorno;
        this.atualizadoEm = agora;
    }

    public String getId() { return id; }
    public String getOrcamentoId() { return orcamentoId; }
    public Long getOsId() { return osId; }
    public BigDecimal getValor() { return valor; }
    public String getMetodo() { return metodo; }
    public StatusPagamento getStatus() { return status; }
    public String getIdExterno() { return idExterno; }
    public String getMotivo() { return motivo; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
}
