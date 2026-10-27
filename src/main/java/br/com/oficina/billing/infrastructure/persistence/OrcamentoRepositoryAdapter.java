package br.com.oficina.billing.infrastructure.persistence;

import br.com.oficina.billing.application.port.out.OrcamentoRepository;
import br.com.oficina.billing.domain.model.ItemOrcamento;
import br.com.oficina.billing.domain.model.Orcamento;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OrcamentoRepositoryAdapter implements OrcamentoRepository {

    private final OrcamentoJpa jpa;

    public OrcamentoRepositoryAdapter(OrcamentoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(Orcamento o) {
        // Atualiza a entidade já carregada (preserva @Version); só cria uma nova se não existir
        OrcamentoEntity e = jpa.findById(o.getId()).orElseGet(OrcamentoEntity::new);
        e.id = o.getId();
        e.osId = o.getOsId();
        e.clienteId = o.getClienteId();
        e.emailCliente = o.getEmailCliente();
        e.status = o.getStatus();
        e.motivoCancelamento = o.getMotivoCancelamento();
        e.valorTotal = o.getValorTotal();
        e.criadoEm = o.getCriadoEm();
        e.atualizadoEm = o.getAtualizadoEm();
        e.itens.clear();
        for (ItemOrcamento i : o.getItens()) {
            e.itens.add(new ItemEmb(i.tipo(), i.codigo(), i.descricao(), i.quantidade(), i.precoUnitario()));
        }
        jpa.saveAndFlush(e);
    }

    @Override
    public Optional<Orcamento> buscarPorId(String id) {
        return jpa.findById(id).map(OrcamentoRepositoryAdapter::paraDominio);
    }

    @Override
    public Optional<Orcamento> buscarPorIdParaAtualizar(String id) {
        return jpa.buscarComBloqueio(id).map(OrcamentoRepositoryAdapter::paraDominio);
    }

    @Override
    public List<Orcamento> buscarPorOs(Long osId) {
        return jpa.findByOsIdOrderByCriadoEmDescIdDesc(osId).stream()
                .map(OrcamentoRepositoryAdapter::paraDominio).toList();
    }

    private static Orcamento paraDominio(OrcamentoEntity e) {
        List<ItemOrcamento> itens = e.itens.stream()
                .map(i -> new ItemOrcamento(i.tipo, i.codigo, i.descricao, i.quantidade, i.precoUnitario))
                .toList();
        return Orcamento.reconstituir(e.id, e.osId, e.clienteId, e.emailCliente, itens, e.status,
                e.motivoCancelamento, e.criadoEm, e.atualizadoEm);
    }
}
