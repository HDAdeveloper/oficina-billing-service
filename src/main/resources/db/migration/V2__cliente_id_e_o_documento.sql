-- O cliente passa a ser identificado pelo documento (CPF/CNPJ, so digitos), como no OS Service.
-- Valores antigos (ids numericos) sao mantidos como texto; so existiam em ambientes de desenvolvimento.
ALTER TABLE orcamento ALTER COLUMN cliente_id TYPE VARCHAR(14) USING cliente_id::text;
