package br.com.oficina.billing.infrastructure.persistence;

import br.com.oficina.billing.domain.model.StatusPagamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PagamentoJpa extends JpaRepository<PagamentoEntity, String> {

    Optional<PagamentoEntity> findFirstByOrcamentoIdAndStatus(String orcamentoId, StatusPagamento status);

    List<PagamentoEntity> findByOrcamentoIdOrderByCriadoEmDescIdDesc(String orcamentoId);

    List<PagamentoEntity> findByStatusAndExpiraEmLessThanEqual(StatusPagamento status, Instant limite);

    /** SELECT ... FOR UPDATE: webhooks repetidos e o vencimento do link não passam um por cima do outro. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PagamentoEntity p where p.id = :id")
    Optional<PagamentoEntity> buscarComBloqueio(@Param("id") String id);
}
