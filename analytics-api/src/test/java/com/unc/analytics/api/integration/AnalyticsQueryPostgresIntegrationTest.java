package com.unc.analytics.api.integration;

import com.unc.analytics.api.analytics.R2dbcRequestLogQueryRepository;
import com.unc.analytics.api.analytics.dto.LatencyMetricsResponse;
import com.unc.analytics.api.analytics.dto.TrafficPulseResponse.BucketEntry;
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Testcontainers(disabledWithoutDocker = true)
class AnalyticsQueryPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("unc_db")
            .withUsername("postgres")
            .withPassword("postgrespassword");

    private static R2dbcRequestLogQueryRepository repository;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID TENANT_C = UUID.randomUUID();

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

                stmt.setObject(1, TENANT_C);
                stmt.setString(2, "tenant-c");
                stmt.setObject(3, TENANT_C);
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

        repository = new R2dbcRequestLogQueryRepository(databaseClient);
    }

    @Test
    @DisplayName("Latency percentile query computes p95 and p99 strictly scoped to requesting tenant")
    void testTenantScopedLatencyPercentiles() throws SQLException {
        // Seed Tenant A with durations: [10, 20, 30, 40, 50, 60, 70, 80, 90, 500]
        long[] durationsA = {10, 20, 30, 40, 50, 60, 70, 80, 90, 500};
        // Seed Tenant B with disjoint durations: [1000, 2000, 3000, 4000, 5000]
        long[] durationsB = {1000, 2000, 3000, 4000, 5000};

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO request_logs (id, tenant_id, method, path, status, latency_ms, created_at) " +
                            "VALUES (?, ?, 'GET', '/api/v1/test', 200, ?, CURRENT_TIMESTAMP)")) {
                for (long d : durationsA) {
                    stmt.setObject(1, UUID.randomUUID());
                    stmt.setObject(2, TENANT_A);
                    stmt.setLong(3, d);
                    stmt.executeUpdate();
                }
                for (long d : durationsB) {
                    stmt.setObject(1, UUID.randomUUID());
                    stmt.setObject(2, TENANT_B);
                    stmt.setLong(3, d);
                    stmt.executeUpdate();
                }
            }
        }

        LatencyMetricsResponse metricsA = repository.getLatencyMetrics(TENANT_A).block();
        assertThat(metricsA).isNotNull();
        // For values [10..90, 500], p95 is ~315.5 and p99 is ~463.1
        assertThat(metricsA.getP95()).isCloseTo(315.5, within(1.0));
        assertThat(metricsA.getP99()).isCloseTo(463.1, within(1.0));

        LatencyMetricsResponse metricsB = repository.getLatencyMetrics(TENANT_B).block();
        assertThat(metricsB).isNotNull();
        // Tenant B's percentiles are computed strictly from its own rows
        assertThat(metricsB.getP95()).isGreaterThan(4000.0);
        assertThat(metricsB.getP99()).isGreaterThan(4500.0);

        // Tenant C has no logs, returns 0.0
        LatencyMetricsResponse metricsC = repository.getLatencyMetrics(TENANT_C).block();
        assertThat(metricsC).isNotNull();
        assertThat(metricsC.getP95()).isEqualTo(0.0);
        assertThat(metricsC.getP99()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Traffic pulse query returns ordered time-bucketed counts strictly scoped to tenant")
    void testTenantScopedTrafficPulseBucketing() throws SQLException {
        Instant baseMinute = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(10, ChronoUnit.MINUTES);
        Instant m1 = baseMinute.plus(1, ChronoUnit.MINUTES);
        Instant m2 = baseMinute.plus(2, ChronoUnit.MINUTES);
        Instant m3 = baseMinute.plus(3, ChronoUnit.MINUTES);

        // Seed Tenant A: m1 has 3 requests, m2 has 5 requests, m3 has 2 requests
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO request_logs (id, tenant_id, method, path, status, latency_ms, created_at) " +
                            "VALUES (?, ?, 'GET', '/api/test', 200, 15, ?)")) {

                insertBatch(stmt, TENANT_A, m1, 3);
                insertBatch(stmt, TENANT_A, m2, 5);
                insertBatch(stmt, TENANT_A, m3, 2);

                // Seed Tenant B at m2 with 100 requests to verify tenant isolation
                insertBatch(stmt, TENANT_B, m2, 100);
            }
        }

        List<BucketEntry> bucketsA = repository.getTrafficPulse(TENANT_A).collectList().block();
        assertThat(bucketsA).isNotNull();
        // Find our seeded buckets (filter to m1, m2, m3 in case prior tests inserted other minutes)
        List<BucketEntry> seededBuckets = bucketsA.stream()
                .filter(b -> b.getBucketStart().equals(m1) || b.getBucketStart().equals(m2) || b.getBucketStart().equals(m3))
                .toList();

        assertThat(seededBuckets).hasSize(3);
        assertThat(seededBuckets.get(0).getBucketStart()).isEqualTo(m1);
        assertThat(seededBuckets.get(0).getRequestCount()).isEqualTo(3L);

        assertThat(seededBuckets.get(1).getBucketStart()).isEqualTo(m2);
        assertThat(seededBuckets.get(1).getRequestCount()).isEqualTo(5L); // Never 105

        assertThat(seededBuckets.get(2).getBucketStart()).isEqualTo(m3);
        assertThat(seededBuckets.get(2).getRequestCount()).isEqualTo(2L);
    }

    private void insertBatch(PreparedStatement stmt, UUID tenantId, Instant timestamp, int count) throws SQLException {
        for (int i = 0; i < count; i++) {
            stmt.setObject(1, UUID.randomUUID());
            stmt.setObject(2, tenantId);
            stmt.setTimestamp(3, Timestamp.from(timestamp.plusMillis(i * 100L)));
            stmt.executeUpdate();
        }
    }
}
