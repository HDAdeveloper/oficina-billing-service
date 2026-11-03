# Billing Service — documentação do serviço

## Responsabilidade

Contexto **Faturamento** (subdomínio de suporte). Cria o orçamento de uma OS, cobra, cancela e estorna. Não conhece clientes nem veículos:
só guarda o `osId` e o `clienteId` (o CPF/CNPJ do cliente, texto) recebidos no comando. Participa da Saga **apenas por mensagens**, sem iniciar nada sozinho.

- **Banco:** PostgreSQL 16 (`oficina_billing`), migrações Flyway `V1` (esquema), `V2` (cliente = documento) e `V3` (pagamento por link).
- **Tabelas:** `orcamento`, `orcamento_item`, `pagamento`, `outbox_evento`, `mensagem_processada`.
- **Porta:** 8082. Swagger UI em `/swagger-ui.html`.

## API REST (consulta)

Todas exigem JWT com papel `ADMIN`. A escrita acontece só por comando da Saga.

| Rota | Descrição |
|---|---|
| `GET /api/orcamentos/{id}` | Orçamento com itens e status |
| `GET /api/orcamentos/{id}/pagamentos` | Pagamentos do orçamento |
| `GET /api/ordens/{osId}/orcamentos` | Orçamentos de uma OS |
| `POST /webhooks/mercadopago` | Aviso do Mercado Pago (público, só com `provedor=mercadopago`; assinatura obrigatória) |
| `POST /api/simulador/pagamentos/{id}/aprovar` / `/expirar` | Simulam o cliente pagando ou o link vencendo (só com `provedor=simulado`) |
| `/actuator/health` | Saúde (público) |

## Mensageria

| Direção | Mensagem |
|---|---|
| Consome (fila `billing-commands`) | `CRIAR_ORCAMENTO`, `COBRAR_ORCAMENTO`, `CANCELAR_ORCAMENTO`, `ESTORNAR_PAGAMENTO` |
| Publica (tópico `oficina-saga-events`) | `ORCAMENTO_CRIADO`, `ORCAMENTO_FALHOU`, `ORCAMENTO_CANCELADO`, `LINK_PAGAMENTO_GERADO`, `PAGAMENTO_APROVADO`, `PAGAMENTO_RECUSADO`, `PAGAMENTO_ESTORNADO` |

Eventos saem pela **outbox transacional** (`outbox_evento`). Cada comando é idempotente: repetir `CANCELAR_ORCAMENTO` ou `ESTORNAR_PAGAMENTO` não tem efeito
extra, e `mensagem_processada` descarta mensagens repetidas.

## Pagamento (Mercado Pago, Checkout Pro)

O domínio usa a porta `PagamentoGateway` (camada anticorrupção). Há dois adaptadores, escolhidos por `billing.pagamento.provedor`
(variável `PAGAMENTO_PROVEDOR`):

| Provedor | Quando usar | Comportamento |
|---|---|---|
| `simulado` (padrão) | local, CI, testes | o `metodo` escolhe o resultado (`APRO`, `FUND`, `OTHE`, `ERRO`, `ERRO_ESTORNO`); `LINK` gera um link falso e espera o endpoint de simulação |
| `mercadopago` | conta de testes ou produção | cada cobrança cria uma **preferência** (Checkout Pro) e devolve o link para o cliente pagar na página do Mercado Pago |

**Fluxo por link:** `COBRAR_ORCAMENTO` → preferência criada → pagamento `AGUARDANDO` + evento `LINK_PAGAMENTO_GERADO` → o cliente paga →
webhook `POST /webhooks/mercadopago` (público, com assinatura `x-signature` conferida por HMAC-SHA256) → o Billing **consulta** o pagamento
(`GET /v1/payments/{id}`) → `APROVADO` + `PAGAMENTO_APROVADO`. Detalhes de segurança e de falhas em [`eventos.md`](../../docs/eventos.md).

- **Estados do pagamento:** `AGUARDANDO` → `APROVADO` → `ESTORNADO`; `AGUARDANDO` → `EXPIRADO` (link vencido ou orçamento cancelado); `RECUSADO` (provedor simulado).
- **Vencimento:** rotina a cada 10 s (`billing.pagamento.intervalo-expiracao-ms`); o link vale `PAGAMENTO_VALIDADE_LINK_MINUTOS` (padrão 1440 = 24 h).
- **Estorno:** `POST /v1/payments/{id}/refunds` (total), com `X-Idempotency-Key`. O mesmo caminho devolve pagamentos tardios ou em duplicidade.
- **Simulador (só com o provedor simulado):** `POST /api/simulador/pagamentos/{id}/aprovar` e `/expirar` (JWT ADMIN).

**Configuração do Mercado Pago (nunca no Git):**

| Variável | O que é |
|---|---|
| `PAGAMENTO_PROVEDOR=mercadopago` | liga o adaptador real |
| `MERCADOPAGO_ACCESS_TOKEN` | Access Token da aplicação (teste: começa com `APP_USR`) |
| `MERCADOPAGO_WEBHOOK_SECRET` | "assinatura secreta" do webhook (painel da aplicação) |
| `MERCADOPAGO_NOTIFICATION_URL` | URL pública HTTPS do webhook (ex.: `https://xxxx.ngrok-free.app/webhooks/mercadopago`) |
| `MERCADOPAGO_USAR_SANDBOX_LINK` | `true` para usar o `sandbox_init_point` |

O serviço **não sobe** com `mercadopago` sem o token e o segredo. Passo a passo completo: [`docs/mercado-pago.md`](../../docs/mercado-pago.md).

## Modelo e regras

Agregados: `Orcamento` (com `ItemOrcamento`) e `Pagamento` (ver [`ddd.md`](ddd.md)). Orçamento: `PENDENTE → PAGO → ESTORNADO` ou `PENDENTE → CANCELADO`.
Um orçamento pago **não** pode ser cancelado, só estornado.

## Estrutura do código

`domain/` · `application/` (`BillingService`, portas) · `infrastructure/` (web, JPA, mensageria, gateway de pagamento, segurança).

## Testes e qualidade

- Testes unitários e de integração por camada (JUnit 5, Mockito); `domain` e `application` testam-se sem Spring.
- Cobertura mínima de **80% das linhas** aplicada no build (JaCoCo em `mvnw verify`) e em cada Pull Request.
- Análise estática no SonarQube Cloud pela pipeline. Evidências de cobertura: [`evidencias/`](evidencias/).
- Como rodar: `.\mvnw.cmd clean verify` (relatório em `target\site\jacoco\index.html`).

## Documentação compartilhada do projeto

| Documento | Conteúdo |
|---|---|
| [`arquitetura.md`](arquitetura.md) | Visão geral, hexagonal, dados, Saga, mensageria, segurança, CI/CD |
| [`ddd.md`](ddd.md) | Contextos, agregados, estados e linguagem ubíqua (imagens em [`diagramas/ddd/`](diagramas/ddd/)) |
| [`eventos.md`](eventos.md) | Comandos, eventos, envelope, fluxo feliz e compensações da Saga |
| [`adr/`](adr/) | Decisões de arquitetura e justificativas |
