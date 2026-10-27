package br.com.oficina.billing.infrastructure.web.controller;

import br.com.oficina.billing.application.port.in.ConsultaOrcamentos;
import br.com.oficina.billing.infrastructure.web.dto.OrcamentoResponse;
import br.com.oficina.billing.infrastructure.web.dto.PagamentoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Orçamentos e pagamentos (consulta)")
public class OrcamentoController {

    private final ConsultaOrcamentos consulta;

    public OrcamentoController(ConsultaOrcamentos consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/api/orcamentos/{id}")
    @Operation(summary = "Busca um orçamento pelo id")
    public OrcamentoResponse buscar(@PathVariable String id) {
        return OrcamentoResponse.de(consulta.buscarPorId(id));
    }

    @GetMapping("/api/orcamentos/{id}/pagamentos")
    @Operation(summary = "Lista as tentativas de pagamento de um orçamento (mais recentes primeiro)")
    public List<PagamentoResponse> pagamentos(@PathVariable String id) {
        return consulta.listarPagamentos(id).stream().map(PagamentoResponse::de).toList();
    }

    @GetMapping("/api/ordens/{osId}/orcamentos")
    @Operation(summary = "Lista os orçamentos de uma OS (mais recentes primeiro)")
    public List<OrcamentoResponse> porOs(@PathVariable Long osId) {
        return consulta.listarPorOs(osId).stream().map(OrcamentoResponse::de).toList();
    }
}
