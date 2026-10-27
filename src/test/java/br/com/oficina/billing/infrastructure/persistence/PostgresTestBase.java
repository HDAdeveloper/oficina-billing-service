package br.com.oficina.billing.infrastructure.persistence;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Um PostgreSQL real (contêiner; precisa do Docker rodando) compartilhado pelos testes de banco. */
public abstract class PostgresTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    protected static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        // Nos testes não há SQS/SNS: os componentes de mensageria ficam desligados
        registro.add("billing.mensageria.habilitada", () -> "false");
    }
}
