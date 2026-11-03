package br.com.oficina.billing.domain.model;

import br.com.oficina.billing.domain.exception.RegraDeNegocioException;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tentativa de cobrança de um orçamento. Uma recusa também fica registrada, para auditoria.
 * No fluxo por link: AGUARDANDO → APROVADO → ESTORNADO, ou AGUARDANDO → EXPIRADO.
 */
public class Pagamento {

    private final String id;
    private final String orcamentoId;
    private final Long osId;
    private final BigDecimal valor;
    private final String metodo;
    private StatusPagamento status;
    private String idExterno;
    private String motivo;
    private final String sagaId;
    private final String correlationId;
    private final String linkPagamento;
    private final Instant expiraEm;
    private final Instant criadoEm;
    private Instant atualizadoEm;

    private Pagamento(String id, String orcamentoId, Long osId, BigDecimal valor, String metodo,
                      StatusPagamento status, String idExterno, String motivo, String sagaId,
                      String correlationId, String linkPagamento, Instant expiraEm, Instant criadoEm,
                      Instant atualizadoEm) {
        this.id = id;
        this.orcamentoId = orcamentoId;
        this.osId = osId;
        this.valor = valor;
        this.metodo = metodo;
        this.status = status;
        this.idExterno = idExterno;
        this.motivo = motivo;
        this.sagaId = sagaId;
        this.correlationId = correlationId;
        this.linkPagamento = linkPagamento;
        this.expiraEm = expiraEm;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static Pagamento aprovado(String id, Orcamento orcamento, String metodo, String idExterno,
                                     Instant agora) {
        return new Pagamento(id, orcamento.getId(), orcamento.getOsId(), orcamento.getValorTotal(), metodo,
                StatusPagamento.APROVADO, idExterno, null, null, null, null, null, agora, agora);
    }

    public static Pagamento recusado(String id, Orcamento orcamento, String metodo, String motivo,
                                     Instant agora) {
        return new Pagamento(id, orcamento.getId(), orcamento.getOsId(), orcamento.getValorTotal(), metodo,
                StatusPagamento.RECUSADO, null, motivo, null, null, null, null, agora, agora);
    }

    /**
     * Link de pagamento gerado e enviado ao cliente. {@code idExterno} é, por enquanto, o id da preferência no
     * provedor; vira o id do pagamento quando ele for aprovado. A Saga e a correlação ficam guardadas porque a
     * confirmação chega depois, por webhook, fora de qualquer mensagem da Saga.
     */
    public static Pagamento aguardando(String id, Orcamento orcamento, String metodo, String idExterno,
                                       String linkPagamento, Instant expiraEm, String sagaId,
                                       String correlationId, Instant agora) {
        if (linkPagamento == null || linkPagamento.isBlank()) {
            throw new RegraDeNegocioException("Link de pagamento é obrigatório");
        }
        if (expiraEm == null) {
            throw new RegraDeNegocioException("Validade do link de pagamento é obrigatória");
        }
        return new Pagamento(id, orcamento.getId(), orcamento.getOsId(), orcamento.getValorTotal(), metodo,
                StatusPagamento.AGUARDANDO, idExterno, null, sagaId, correlationId, linkPagamento, expiraEm,
                agora, agora);
    }

    public static Pagamento reconstituir(String id, String orcamentoId, Long osId, BigDecimal valor,
                                         String metodo, StatusPagamento status, String idExterno,
                                         String motivo, Instant criadoEm, Instant atualizadoEm) {
        return new Pagamento(id, orcamentoId, osId, valor, metodo, status, idExterno, motivo, null, null, null,
                null, criadoEm, atualizadoEm);
    }

    public static Pagamento reconstituir(String id, String orcamentoId, Long osId, BigDecimal valor,
                                         String metodo, StatusPagamento status, String idExterno,
                                         String motivo, String sagaId, String correlationId,
                                         String linkPagamento, Instant expiraEm, Instant criadoEm,
                                         Instant atualizadoEm) {
        return new Pagamento(id, orcamentoId, osId, valor, metodo, status, idExterno, motivo, sagaId,
                correlationId, linkPagamento, expiraEm, criadoEm, atualizadoEm);
    }

    // ------------------------------------------------------------ transições

    /** O cliente pagou o link: guarda o id do pagamento no provedor (usado depois no estorno). */
    public void aprovar(String idPagamentoExterno, Instant agora) {
        if (status != StatusPagamento.AGUARDANDO) {
            throw new RegraDeNegocioException("Pagamento " + id + " está " + status + " e não pode ser aprovado");
        }
        if (idPagamentoExterno == null || idPagamentoExterno.isBlank()) {
            throw new RegraDeNegocioException("Id do pagamento no provedor é obrigatório");
        }
        this.status = StatusPagamento.APROVADO;
        this.idExterno = idPagamentoExterno;
        this.atualizadoEm = agora;
    }

    /**
     * O link venceu ou o orçamento foi cancelado antes do pagamento. Se já estiver expirado, não faz nada e
     * devolve {@code false}.
     */
    public boolean expirar(String motivoExpiracao, Instant agora) {
        if (status == StatusPagamento.EXPIRADO) {
            return false;
        }
        if (status != StatusPagamento.AGUARDANDO) {
            throw new RegraDeNegocioException("Pagamento " + id + " está " + status + " e não pode expirar");
        }
        this.status = StatusPagamento.EXPIRADO;
        this.motivo = motivoExpiracao;
        this.atualizadoEm = agora;
        return true;
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

    /** Anota algo no pagamento sem mudar o status (ex.: pagamento tardio devolvido ao cliente). */
    public void anotar(String texto, Instant agora) {
        this.motivo = texto;
        this.atualizadoEm = agora;
    }

    public boolean estaVencido(Instant agora) {
        return status == StatusPagamento.AGUARDANDO && expiraEm != null && !expiraEm.isAfter(agora);
    }

    public String getId() { return id; }
    public String getOrcamentoId() { return orcamentoId; }
    public Long getOsId() { return osId; }
    public BigDecimal getValor() { return valor; }
    public String getMetodo() { return metodo; }
    public StatusPagamento getStatus() { return status; }
    public String getIdExterno() { return idExterno; }
    public String getMotivo() { return motivo; }
    public String getSagaId() { return sagaId; }
    public String getCorrelationId() { return correlationId; }
    public String getLinkPagamento() { return linkPagamento; }
    public Instant getExpiraEm() { return expiraEm; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
}
