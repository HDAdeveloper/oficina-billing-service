package br.com.oficina.billing.infrastructure.web.controller;

import br.com.oficina.billing.application.port.in.ConfirmacaoPagamentos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Faz o papel do cliente e do Mercado Pago quando o provedor é o simulado: "paga" ou "vence" o link de um
 * pagamento com método LINK. Só existe com {@code billing.pagamento.provedor=simulado}.
 */
@RestController
@Tag(name = "Simulador de pagamento por link (só com o provedor simulado)")
@ConditionalOnProperty(name = "billing.pagamento.provedor", havingValue = "simulado", matchIfMissing = true)
public class SimuladorPagamentoController {

    private final ConfirmacaoPagamentos confirmacao;

    public SimuladorPagamentoController(ConfirmacaoPagamentos confirmacao) {
        this.confirmacao = confirmacao;
    }

    @PostMapping("/api/simulador/pagamentos/{id}/aprovar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Simula o cliente pagando o link (a Saga segue para a reserva de estoque)")
    public void aprovar(@PathVariable String id) {
        confirmacao.aprovarSimulado(id);
    }

    @PostMapping("/api/simulador/pagamentos/{id}/expirar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Simula o link vencendo sem pagamento (a Saga roda a compensação A)")
    public void expirar(@PathVariable String id) {
        confirmacao.expirarAgora(id);
    }
}
