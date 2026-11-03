-- Pagamento por link: o cliente paga depois e o provedor avisa por webhook.
-- A Saga e a correlacao ficam no pagamento porque a confirmacao chega fora de qualquer mensagem da Saga.
ALTER TABLE pagamento ADD COLUMN saga_id         VARCHAR(64);
ALTER TABLE pagamento ADD COLUMN correlation_id  VARCHAR(100);
ALTER TABLE pagamento ADD COLUMN link_pagamento  VARCHAR(500);
ALTER TABLE pagamento ADD COLUMN expira_em       TIMESTAMP WITH TIME ZONE;

-- Um link aguardando por orcamento (a cobranca repetida devolve o mesmo link)
CREATE UNIQUE INDEX uk_pagamento_aguardando_por_orcamento ON pagamento (orcamento_id) WHERE status = 'AGUARDANDO';
-- O vencimento varre so os links ainda abertos
CREATE INDEX idx_pagamento_aguardando_expira ON pagamento (expira_em) WHERE status = 'AGUARDANDO';
