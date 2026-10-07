package com.unc.gateway.core.integration;

import com.unc.gateway.core.GatewayCoreApplication;
import com.unc.gateway.core.cache.RouteCache;
import com.unc.gateway.core.cache.RouteEntry;
import com.unc.gateway.core.reconciliation.ReconciliationPoller;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GatewayCoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Phase 15: Gateway Core Reconciliation Poll Safety Net Integration Test")
class GatewayCoreReconciliationIntegrationTest {

    private static PostgreSQLContainer<?> postgresContainer;
    private static String jdbcUrl;
    private static String r2dbcUrl;
    private static String username;
    private static String password;
    private static boolean postgresAvailable = false;

    private static final UUID TENANT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SERVICE_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID ROUTE_1_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID ROUTE_2_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    @BeforeAll
    static void initPostgres() {
        try {
            postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("unc_db")
                    .withUsername("postgres")
                    .withPassword("postgrespassword");
            postgresContainer.start();
            jdbcUrl = postgresContainer.getJdbcUrl();
            username = postgresContainer.getUsername();
            password = postgresContainer.getPassword();
            r2dbcUrl = String.format("r2dbc:postgresql://%s:%d/%s",
                    postgresContainer.getHost(),
                    postgresContainer.getFirstMappedPort(),
                    postgresContainer.getDatabaseName());
            postgresAvailable = true;
        } catch (Throwable t) {
            String localJdbc = "jdbc:postgresql://localhost:5435/unc_db";
            try (Connection conn = DriverManager.getConnection(localJdbc, "postgres", "postgrespassword")) {
                jdbcUrl = localJdbc;
                username = "postgres";
                password = "postgrespassword";
                r2dbcUrl = "r2dbc:postgresql://localhost:5435/unc_db";
                postgresAvailable = true;
            } catch (SQLException ignored) {
                postgresAvailable = false;
            }
        }

        Assumptions.assumeTrue(postgresAvailable, "PostgreSQL is required for reconciliation integration testing");

        // Run Flyway migrations
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .ignoreMigrationPatterns("*:missing")
                .load();
        flyway.migrate();

        // Seed tenant, service, and routes
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            // Seed tenant
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO tenants (id, name, tenant_id, api_key, status) VALUES (?, ?, ?, ?, ?) " +
                            "ON CONFLICT (id) DO NOTHING")) {
                stmt.setObject(1, TENANT_ID);
                stmt.setString(2, "reconciliation-tenant");
                stmt.setObject(3, TENANT_ID);
                stmt.setString(4, "api-key-reconciliation");
                stmt.setString(5, "ACTIVE");
                stmt.executeUpdate();
            }

            // Seed service
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO services (id, tenant_id, name, url) VALUES (?, ?, ?, ?) " +
                            "ON CONFLICT (id) DO NOTHING")) {
                stmt.setObject(1, SERVICE_ID);
                stmt.setObject(2, TENANT_ID);
                stmt.setString(3, "reconciliation-service");
                stmt.setString(4, "http://mock-upstream:8080");
                stmt.executeUpdate();
            }

            // Seed route 1 (/reconcile/demo)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO routes (id, tenant_id, service_id, name, paths, strip_path) VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT (id) DO NOTHING")) {
                stmt.setObject(1, ROUTE_1_ID);
                stmt.setObject(2, TENANT_ID);
                stmt.setObject(3, SERVICE_ID);
                stmt.setString(4, "route-demo");
                stmt.setString(5, "/reconcile/demo");
                stmt.setBoolean(6, true);
                stmt.executeUpdate();
            }

            // Seed route 2 (/reconcile/orders)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO routes (id, tenant_id, service_id, name, paths, strip_path) VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT (id) DO NOTHING")) {
                stmt.setObject(1, ROUTE_2_ID);
                stmt.setObject(2, TENANT_ID);
                stmt.setObject(3, SERVICE_ID);
                stmt.setString(4, "route-orders");
                stmt.setString(5, "/reconcile/orders");
                stmt.setBoolean(6, false);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to seed initial data", e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgresAvailable) {
            registry.add("spring.r2dbc.url", () -> r2dbcUrl);
            registry.add("spring.r2dbc.username", () -> username);
            registry.add("spring.r2dbc.password", () -> password);
            registry.add("gateway.reconciliation.interval-ms", () -> "60000");
        }
    }

    @Autowired
    private RouteCache routeCache;

    @Autowired
    private ReconciliationPoller reconciliationPoller;

    @Autowired(required = false)
    private ScheduledTaskHolder scheduledTaskHolder;

    @Test
    @DisplayName("manually mutated in-process RouteCache converges back to match seeded DB rows after ReconciliationPoller runs")
    void testCacheDivergenceConvergesBackToDatabaseRows() {
        // Verify initial seeded routes are present
        reconciliationPoller.poll();
        assertThat(routeCache.lookup("/reconcile/demo", TENANT_ID)).isPresent();
        assertThat(routeCache.lookup("/reconcile/orders", TENANT_ID)).isPresent();

        // 1. Manually mutate in-process RouteCache to simulate divergence (missed NOTIFY)
        // Evict legitimate route 1
        routeCache.evict(ROUTE_1_ID);
        // Inject a stale rogue route not in the database
        UUID rogueRouteId = UUID.randomUUID();
        routeCache.put(new RouteEntry(rogueRouteId, SERVICE_ID, TENANT_ID, "/rogue/stale", "http://stale:8080", true));

        // Confirm divergence
        assertThat(routeCache.lookup("/reconcile/demo", TENANT_ID)).isEmpty();
        assertThat(routeCache.lookup("/rogue/stale", TENANT_ID)).isPresent();

        // 2. Invoke ReconciliationPoller once directly
        reconciliationPoller.poll();

        // 3. Verify RouteCache converged back to match seeded DB rows
        Optional<RouteEntry> restoredRoute1 = routeCache.lookup("/reconcile/demo", TENANT_ID);
        assertThat(restoredRoute1).isPresent();
        assertThat(restoredRoute1.get().routeId()).isEqualTo(ROUTE_1_ID);
        assertThat(restoredRoute1.get().upstreamUrl()).isEqualTo("http://mock-upstream:8080");

        Optional<RouteEntry> route2 = routeCache.lookup("/reconcile/orders", TENANT_ID);
        assertThat(route2).isPresent();
        assertThat(route2.get().routeId()).isEqualTo(ROUTE_2_ID);

        // Rogue route must be evicted by the bulk-replace
        assertThat(routeCache.lookup("/rogue/stale", TENANT_ID)).isEmpty();
    }

    @Test
    @DisplayName("missed NOTIFY from trigger-suppressed UPDATE converges to updated state upon reconciliation poll")
    void testMissedNotifyOnUpdateConvergesOnPoll() throws SQLException {
        // Ensure starting state is synchronized
        reconciliationPoller.poll();

        String updatedPath = "/reconcile/demo-v2";

        // Suppress trigger, perform update, restore trigger (simulating a missed NOTIFY / dropped connection)
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            try (PreparedStatement disableStmt = conn.prepareStatement("ALTER TABLE routes DISABLE TRIGGER routes_notify_trigger");
                 PreparedStatement updateStmt = conn.prepareStatement("UPDATE routes SET paths = ? WHERE id = ?");
                 PreparedStatement enableStmt = conn.prepareStatement("ALTER TABLE routes ENABLE TRIGGER routes_notify_trigger")) {

                disableStmt.execute();

                updateStmt.setString(1, updatedPath);
                updateStmt.setObject(2, ROUTE_1_ID);
                updateStmt.executeUpdate();

                enableStmt.execute();
            }
        }

        // Without reconciliation, cache still holds old path (modeling stale state)
        // (Note: because trigger was disabled, no NOTIFY was sent)
        assertThat(routeCache.lookup("/reconcile/demo", TENANT_ID)).isPresent();
        assertThat(routeCache.lookup(updatedPath, TENANT_ID)).isEmpty();

        // Invoke ReconciliationPoller directly
        reconciliationPoller.poll();

        // Stale entry is gone; updated entry is now present in RouteCache
        assertThat(routeCache.lookup("/reconcile/demo", TENANT_ID)).isEmpty();
        Optional<RouteEntry> updatedRoute = routeCache.lookup(updatedPath, TENANT_ID);
        assertThat(updatedRoute).isPresent();
        assertThat(updatedRoute.get().routeId()).isEqualTo(ROUTE_1_ID);
        assertThat(updatedRoute.get().path()).isEqualTo(updatedPath);

        // Reset database row back to original path for test repeatability
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement stmt = conn.prepareStatement("UPDATE routes SET paths = '/reconcile/demo' WHERE id = ?")) {
            stmt.setObject(1, ROUTE_1_ID);
            stmt.executeUpdate();
        }
        reconciliationPoller.poll();
    }

    @Test
    @DisplayName("scheduled tasks in Spring application context include ReconciliationPoller")
    void testScheduledTaskRegistration() {
        assertThat(scheduledTaskHolder).isNotNull();
        Set<ScheduledTask> tasks = scheduledTaskHolder.getScheduledTasks();
        assertThat(tasks).isNotEmpty();

        boolean pollerScheduled = tasks.stream()
                .anyMatch(task -> task.getTask().toString().contains("ReconciliationPoller") ||
                        task.toString().contains("ReconciliationPoller"));

        assertThat(pollerScheduled)
                .as("ReconciliationPoller must be registered with Spring scheduling")
                .isTrue();
    }
}
