package br.com.oficina.billing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MensagemProcessadaJpa extends JpaRepository<MensagemProcessadaEntity, String> {
}
