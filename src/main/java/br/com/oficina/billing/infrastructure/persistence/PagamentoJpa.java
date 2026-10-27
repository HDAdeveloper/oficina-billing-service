package br.com.oficina.billing.infrastructure.persistence;

import br.com.oficina.billing.domain.model.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PagamentoJpa extends JpaRepository<PagamentoEntity, String> {

    Optional<PagamentoEntity> findFirstByOrcamentoIdAndStatus(String orcamentoId, StatusPagamento status);

    List<PagamentoEntity> findByOrcamentoIdOrderByCriadoEmDescIdDesc(String orcamentoId);
}
