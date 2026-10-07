package com.unc.analytics.api.integration;

import com.unc.analytics.api.ingest.IngestRequest;
import com.unc.analytics.api.ingest.R2dbcRequestLogRepository;
import com.unc.analytics.api.ingest.RequestLog;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class AnalyticsIngestPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("unc_db")
            .withUsername("postgres")
            .withPassword("postgrespassword");

    private static R2dbcRequestLogRepository repository;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @BeforeAll
    static void setUp() throws SQLException {
        // Run Flyway migrations (Phase 3 + Phase 4 schemas) against the testcontainer
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // Seed tenant rows into the tenants table
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO tenants (id, name, tenant_id) VALUES (?, ?, ?)")) {
                stmt.setObject(1, TENANT_A);
                stmt.setString(2, "tenant-a");
                stmt.setObject(3, TENANT_A);
                stmt.executeUpdate();

                stmt.setObject(1, TENANT_B);
                stmt.setString(2, "tenant-b");
                stmt.setObject(3, TENANT_B);
                stmt.executeUpdate();
            }
        }

        // Initialize R2DBC connection and repository
        PostgresqlConnectionConfiguration config = PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getFirstMappedPort())
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build();
        PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(config);
        DatabaseClient databaseClient = DatabaseClient.create(connectionFactory);

        repository = new R2dbcRequestLogRepository(databaseClient);
    }

    @Test
    @DisplayName("Ingest requests across distinct tenants enforce tenant_id isolation in PostgreSQL")
    void testTenantScopedIngestionAndIsolation() {
        IngestRequest reqA1 = new IngestRequest("GET", "/api/v1/echo/hello", 200, 12L, "consumer-a", Instant.now());
        IngestRequest reqA2 = new IngestRequest("POST", "/api/v1/echo/items", 201, 35L, "consumer-a", Instant.now());
        IngestRequest reqB1 = new IngestRequest("GET", "/api/v1/users", 200, 20L, "consumer-b", Instant.now());

        // Ingest two rows for Tenant A
        RequestLog savedA1 = repository.save(TENANT_A, reqA1).block();
        RequestLog savedA2 = repository.save(TENANT_A, reqA2).block();

        // Ingest one row for Tenant B
        RequestLog savedB1 = repository.save(TENANT_B, reqB1).block();

        assertThat(savedA1).isNotNull();
        assertThat(savedA1.getTenantId()).isEqualTo(TENANT_A);
        assertThat(savedA2).isNotNull();
        assertThat(savedA2.getTenantId()).isEqualTo(TENANT_A);
        assertThat(savedB1).isNotNull();
        assertThat(savedB1.getTenantId()).isEqualTo(TENANT_B);

        // Query Tenant A rows
        List<RequestLog> tenantALogs = repository.findByTenantId(TENANT_A).collectList().block();
        assertThat(tenantALogs).isNotNull();
        assertThat(tenantALogs).extracting(RequestLog::getId)
                .contains(savedA1.getId(), savedA2.getId())
                .doesNotContain(savedB1.getId());
        assertThat(tenantALogs).allSatisfy(r -> assertThat(r.getTenantId()).isEqualTo(TENANT_A));

        // Query Tenant B rows
        List<RequestLog> tenantBLogs = repository.findByTenantId(TENANT_B).collectList().block();
        assertThat(tenantBLogs).isNotNull();
        assertThat(tenantBLogs).extracting(RequestLog::getId)
                .contains(savedB1.getId())
                .doesNotContain(savedA1.getId(), savedA2.getId());
        assertThat(tenantBLogs).allSatisfy(r -> assertThat(r.getTenantId()).isEqualTo(TENANT_B));

        // Cross-tenant get by ID must return empty
        StepVerifier.create(repository.findByIdAndTenantId(savedB1.getId(), TENANT_A))
                .verifyComplete();

        StepVerifier.create(repository.findByIdAndTenantId(savedA1.getId(), TENANT_B))
                .verifyComplete();

        // Count must match
        Long countA = repository.countByTenantId(TENANT_A).block();
        Long countB = repository.countByTenantId(TENANT_B).block();
        assertThat(countA).isGreaterThanOrEqualTo(2L);
        assertThat(countB).isGreaterThanOrEqualTo(1L);
    }
}
