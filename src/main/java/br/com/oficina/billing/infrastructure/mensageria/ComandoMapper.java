package br.com.oficina.billing.infrastructure.mensageria;

import br.com.oficina.billing.application.port.in.ComandoBilling;
import br.com.oficina.billing.application.port.in.ComandoBilling.CancelarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.CobrarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.CriarOrcamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.EstornarPagamento;
import br.com.oficina.billing.application.port.in.ComandoBilling.OrcamentoInvalido;
import br.com.oficina.billing.application.port.in.ContextoMensagem;
import br.com.oficina.billing.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.domain.model.ItemOrcamento;
import br.com.oficina.billing.domain.model.TipoItem;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Traduz o envelope da mensagem no comando da aplicação. */
public class ComandoMapper {

    public record Mapeado(ContextoMensagem contexto, ComandoBilling comando) {
    }

    public Mapeado mapear(EnvelopeMensagem env) {
        if (env == null || vazio(env.type())) {
            throw new MensagemInvalidaException("Mensagem sem o campo type");
        }
        if (vazio(env.messageId())) {
            throw new MensagemInvalidaException("Mensagem " + env.type() + " sem messageId");
        }
        JsonNode p = env.payload();
        ComandoBilling comando = switch (env.type()) {
            case "CRIAR_ORCAMENTO" -> criar(p);
            case "COBRAR_ORCAMENTO" -> new CobrarOrcamento(obrigatorio(p, "orcamentoId"), decimal(p, "valorTotal"),
                    texto(p, "metodo"));
            case "CANCELAR_ORCAMENTO" -> new CancelarOrcamento(obrigatorio(p, "orcamentoId"), texto(p, "motivo"));
            case "ESTORNAR_PAGAMENTO" -> new EstornarPagamento(obrigatorio(p, "pagamentoId"), texto(p, "motivo"));
            default -> throw new MensagemInvalidaException("Comando desconhecido para o Billing: " + env.type());
        };
        return new Mapeado(new ContextoMensagem(env.messageId(), env.sagaId(), env.osId(), env.correlationId()),
                comando);
    }

    private ComandoBilling criar(JsonNode p) {
        try {
            List<ItemOrcamento> itens = new ArrayList<>();
            JsonNode lista = p == null ? null : p.get("itens");
            if (lista != null && lista.isArray()) {
                for (JsonNode i : lista) {
                    itens.add(new ItemOrcamento(tipoItem(texto(i, "tipo")), texto(i, "codigo"), texto(i, "descricao"),
                            inteiro(i, "quantidade"), decimal(i, "precoUnitario")));
                }
            }
            Long clienteId = p != null && p.hasNonNull("clienteId") ? p.get("clienteId").asLong() : null;
            return new CriarOrcamento(clienteId, texto(p, "emailCliente"), itens, decimal(p, "valorTotal"));
        } catch (RegraDeNegocioException | NumberFormatException e) {
            // Dado inválido nunca passa na próxima tentativa: o Billing responde ORCAMENTO_FALHOU
            String motivo = e instanceof NumberFormatException ? "Valor numérico inválido no pedido" : e.getMessage();
            return new OrcamentoInvalido(motivo);
        }
    }

    private static TipoItem tipoItem(String valor) {
        if (valor == null) {
            throw new RegraDeNegocioException("Tipo do item é obrigatório");
        }
        try {
            return TipoItem.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RegraDeNegocioException("Tipo de item inválido: " + valor);
        }
    }

    private static String obrigatorio(JsonNode p, String campo) {
        String valor = texto(p, campo);
        if (vazio(valor)) {
            throw new MensagemInvalidaException("Payload sem o campo obrigatório " + campo);
        }
        return valor;
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode n = no == null ? null : no.get(campo);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static int inteiro(JsonNode no, String campo) {
        JsonNode n = no == null ? null : no.get(campo);
        return n == null || !n.canConvertToInt() ? 0 : n.asInt();
    }

    private static BigDecimal decimal(JsonNode no, String campo) {
        JsonNode n = no == null ? null : no.get(campo);
        if (n == null || n.isNull()) {
            return null;
        }
        return n.isNumber() ? n.decimalValue() : new BigDecimal(n.asText());
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
