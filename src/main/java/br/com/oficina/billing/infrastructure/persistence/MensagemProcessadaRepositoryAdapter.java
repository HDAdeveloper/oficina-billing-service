package br.com.oficina.billing.infrastructure.persistence;

import br.com.oficina.billing.application.port.out.MensagemProcessadaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class MensagemProcessadaRepositoryAdapter implements MensagemProcessadaRepository {

    private final MensagemProcessadaJpa jpa;

    public MensagemProcessadaRepositoryAdapter(MensagemProcessadaJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean jaProcessada(String messageId) {
        return jpa.existsById(messageId);
    }

    @Override
    public void registrar(String messageId, Instant quando) {
        // Chave duplicada (duas entregas simultâneas) estoura aqui e desfaz a transação da segunda
        jpa.saveAndFlush(new MensagemProcessadaEntity(messageId, quando));
    }
}
