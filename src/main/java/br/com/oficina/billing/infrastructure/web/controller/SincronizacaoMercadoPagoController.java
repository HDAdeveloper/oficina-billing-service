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
 * Conciliação manual: faz o que o webhook faria, a pedido do administrador. Serve quando o aviso do Mercado Pago não
 * chega (as contas de teste não enviam notificações; o aviso pode ter se perdido) e para demonstrar o fluxo.
 * O pagamento continua sendo consultado no provedor, então nada é aprovado "na palavra".
 */
@RestController
@Tag(name = "Conciliação com o Mercado Pago (só com o provedor mercadopago)")
@ConditionalOnProperty(name = "billing.pagamento.provedor", havingValue = "mercadopago")
public class SincronizacaoMercadoPagoController {

    private final ConfirmacaoPagamentos confirmacao;

    public SincronizacaoMercadoPagoController(ConfirmacaoPagamentos confirmacao) {
        this.confirmacao = confirmacao;
    }

    @PostMapping("/api/mercadopago/pagamentos/{idPagamentoMercadoPago}/sincronizar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Consulta um pagamento no Mercado Pago e confirma o pagamento do orçamento, se aprovado")
    public void sincronizar(@PathVariable String idPagamentoMercadoPago) {
        confirmacao.pagamentoAtualizado(idPagamentoMercadoPago);
    }
}
