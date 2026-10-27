package br.com.oficina.billing.infrastructure.persistence;

import br.com.oficina.billing.application.port.out.PagamentoRepository;
import br.com.oficina.billing.domain.model.Pagamento;
import br.com.oficina.billing.domain.model.StatusPagamento;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PagamentoRepositoryAdapter implements PagamentoRepository {

    private final PagamentoJpa jpa;

    public PagamentoRepositoryAdapter(PagamentoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(Pagamento p) {
        PagamentoEntity e = jpa.findById(p.getId()).orElseGet(PagamentoEntity::new);
        e.id = p.getId();
        e.orcamentoId = p.getOrcamentoId();
        e.osId = p.getOsId();
        e.valor = p.getValor();
        e.metodo = p.getMetodo();
        e.status = p.getStatus();
        e.idExterno = p.getIdExterno();
        e.motivo = p.getMotivo();
        e.criadoEm = p.getCriadoEm();
        e.atualizadoEm = p.getAtualizadoEm();
        jpa.saveAndFlush(e);
    }

    @Override
    public Optional<Pagamento> buscarPorId(String id) {
        return jpa.findById(id).map(PagamentoRepositoryAdapter::paraDominio);
    }

    @Override
    public Optional<Pagamento> buscarAprovadoDoOrcamento(String orcamentoId) {
        return jpa.findFirstByOrcamentoIdAndStatus(orcamentoId, StatusPagamento.APROVADO)
                .map(PagamentoRepositoryAdapter::paraDominio);
    }

    @Override
    public List<Pagamento> buscarPorOrcamento(String orcamentoId) {
        return jpa.findByOrcamentoIdOrderByCriadoEmDescIdDesc(orcamentoId).stream()
                .map(PagamentoRepositoryAdapter::paraDominio).toList();
    }

    private static Pagamento paraDominio(PagamentoEntity e) {
        return Pagamento.reconstituir(e.id, e.orcamentoId, e.osId, e.valor, e.metodo, e.status,
                e.idExterno, e.motivo, e.criadoEm, e.atualizadoEm);
    }
}
