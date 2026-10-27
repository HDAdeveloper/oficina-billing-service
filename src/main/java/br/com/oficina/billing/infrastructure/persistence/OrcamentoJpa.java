package br.com.oficina.billing.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrcamentoJpa extends JpaRepository<OrcamentoEntity, String> {

    List<OrcamentoEntity> findByOsIdOrderByCriadoEmDescIdDesc(Long osId);

    /** SELECT ... FOR UPDATE: outro comando sobre o mesmo orçamento espera esta transação terminar. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrcamentoEntity o where o.id = :id")
    Optional<OrcamentoEntity> buscarComBloqueio(@Param("id") String id);
}
