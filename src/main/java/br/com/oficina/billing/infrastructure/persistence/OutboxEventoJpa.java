package br.com.oficina.billing.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventoJpa extends JpaRepository<OutboxEventoEntity, Long> {

    /**
     * Lote de pendentes para o relay, bloqueados com SKIP LOCKED: duas réplicas do serviço não pegam
     * os mesmos eventos ao mesmo tempo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxEventoEntity e where e.publicadoEm is null order by e.id asc")
    List<OutboxEventoEntity> buscarPendentesParaPublicar(Pageable limite);

    /** Eventos ainda não enviados, na ordem em que foram gravados. */
    List<OutboxEventoEntity> findByPublicadoEmIsNullOrderByIdAsc(Pageable limite);

    List<OutboxEventoEntity> findByOsIdOrderByIdAsc(Long osId);
}
