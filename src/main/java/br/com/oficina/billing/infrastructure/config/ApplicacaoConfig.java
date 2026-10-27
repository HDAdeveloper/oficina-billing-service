package br.com.oficina.billing.infrastructure.config;

import br.com.oficina.billing.application.port.in.ConsultaOrcamentos;
import br.com.oficina.billing.application.port.in.ProcessadorComandos;
import br.com.oficina.billing.application.port.out.GeradorId;
import br.com.oficina.billing.application.port.out.MensagemProcessadaRepository;
import br.com.oficina.billing.application.port.out.OrcamentoRepository;
import br.com.oficina.billing.application.port.out.PagamentoGateway;
import br.com.oficina.billing.application.port.out.PagamentoRepository;
import br.com.oficina.billing.application.port.out.PublicadorEventos;
import br.com.oficina.billing.application.service.BillingService;
import br.com.oficina.billing.application.service.ConsultaOrcamentosService;
import br.com.oficina.billing.infrastructure.gateway.PagamentoGatewaySimulado;
import br.com.oficina.billing.infrastructure.transaction.TransacaoProxy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** Liga os casos de uso (Java puro) às portas de saída e os envolve em transação. */
@Configuration
public class ApplicacaoConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    public GeradorId geradorId() {
        return new GeradorIdUuid();
    }

    /** Adaptador simulado; o do Mercado Pago entra depois atrás da mesma porta. */
    @Bean
    @ConditionalOnMissingBean(PagamentoGateway.class)
    public PagamentoGateway pagamentoGateway() {
        return new PagamentoGatewaySimulado();
    }

    @Bean
    public ProcessadorComandos processadorComandos(OrcamentoRepository orcamentos, PagamentoRepository pagamentos,
                                                   MensagemProcessadaRepository processadas,
                                                   PublicadorEventos eventos, PagamentoGateway gateway,
                                                   GeradorId ids, Clock relogio, TransactionTemplate tx) {
        return TransacaoProxy.envolver(ProcessadorComandos.class,
                new BillingService(orcamentos, pagamentos, processadas, eventos, gateway, ids, relogio), tx);
    }

    @Bean
    public ConsultaOrcamentos consultaOrcamentos(OrcamentoRepository orcamentos, PagamentoRepository pagamentos,
                                                 TransactionTemplate tx) {
        return TransacaoProxy.envolver(ConsultaOrcamentos.class,
                new ConsultaOrcamentosService(orcamentos, pagamentos), tx);
    }
}
