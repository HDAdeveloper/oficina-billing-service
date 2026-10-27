package br.com.oficina.billing.application.port.out;

import java.util.Map;

/** Evento de resposta ao OS Service. O envelope completo (messageId, data etc.) é montado na infraestrutura. */
public record EventoSaga(String type, Long osId, String sagaId, String correlationId,
                         Map<String, Object> payload) {
}
