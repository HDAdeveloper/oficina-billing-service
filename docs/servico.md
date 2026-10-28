# Billing Service — documentação do serviço

## Responsabilidade

Contexto **Faturamento** (subdomínio de suporte). Cria o orçamento de uma OS, cobra, cancela e estorna. Não conhece clientes nem veículos:
só guarda o `osId` e o `clienteId` recebidos no comando. Participa da Saga **apenas por mensagens**, sem iniciar nada sozinho.

- **Banco:** PostgreSQL 16 (`oficina_billing`), migração Flyway `V1__criar_schema.sql`.
- **Tabelas:** `orcamento`, `orcamento_item`, `pagamento`, `outbox_evento`, `mensagem_processada`.
- **Porta:** 8082. Swagger UI em `/swagger-ui.html`.

## API REST (consulta)

Todas exigem JWT com papel `ADMIN`. A escrita acontece só por comando da Saga.

| Rota | Descrição |
|---|---|
| `GET /api/orcamentos/{id}` | Orçamento com itens e status |
| `GET /api/orcamentos/{id}/pagamentos` | Pagamentos do orçamento |
| `GET /api/ordens/{osId}/orcamentos` | Orçamentos de uma OS |
| `/actuator/health` | Saúde (público) |

## Mensageria

| Direção | Mensagem |
|---|---|
| Consome (fila `billing-commands`) | `CRIAR_ORCAMENTO`, `COBRAR_ORCAMENTO`, `CANCELAR_ORCAMENTO`, `ESTORNAR_PAGAMENTO` |
| Publica (tópico `oficina-saga-events`) | `ORCAMENTO_CRIADO`, `ORCAMENTO_FALHOU`, `ORCAMENTO_CANCELADO`, `PAGAMENTO_APROVADO`, `PAGAMENTO_RECUSADO`, `PAGAMENTO_ESTORNADO` |

Eventos saem pela **outbox transacional** (`outbox_evento`). Cada comando é idempotente: repetir `CANCELAR_ORCAMENTO` ou `ESTORNAR_PAGAMENTO` não tem efeito
extra, e `mensagem_processada` descarta mensagens repetidas.

## Pagamento (Mercado Pago)

O domínio usa a porta `PagamentoGateway` (camada anticorrupção). Hoje o adaptador é **simulado**: o método de pagamento escolhe o resultado
(`APRO` aprova, `FUND` recusa por saldo, `OTHE` recusa, `ERRO` falha, `ERRO_ESTORNO` falha só no estorno). Trocar pelo Mercado Pago real é
um novo adaptador, sem mexer nas regras. O Access Token **nunca** vai para o Git: só variável de ambiente ou Secret.

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
