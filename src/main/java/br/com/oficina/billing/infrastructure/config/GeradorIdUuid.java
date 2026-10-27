package br.com.oficina.billing.infrastructure.config;

import br.com.oficina.billing.application.port.out.GeradorId;

import java.util.UUID;

public class GeradorIdUuid implements GeradorId {
    @Override
    public String novo() {
        return UUID.randomUUID().toString();
    }
}
