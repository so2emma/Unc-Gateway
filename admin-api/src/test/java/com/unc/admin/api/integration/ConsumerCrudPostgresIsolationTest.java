package com.unc.admin.api.integration;

import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the full Phase 8 tenant-isolation CRUD suite against a real {@code postgres:16-alpine}
 * container with the Flyway migrations applied and {@code ddl-auto=validate}, which additionally
 * proves the JPA entities match the migrated PostgreSQL schema (including the {@code jsonb} plugin
 * config column).
 * <p>
 * Skipped automatically when no Docker daemon is reachable; the inherited assertions still run
 * against H2 in {@link ConsumerCrudTenantIsolationTest}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration"
})
@DisplayName("Phase 8 CRUD tenant isolation on PostgreSQL 16")
class ConsumerCrudPostgresIsolationTest extends ConsumerCrudTenantIsolationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("unc_db")
            .withUsername("postgres")
            .withPassword("postgrespassword");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
