package com.unc.gateway.core.integration;

import com.unc.gateway.core.GatewayCoreApplication;
import com.unc.gateway.core.cache.RouteCache;
import com.unc.gateway.core.cache.RouteEntry;
import com.unc.gateway.core.listen.RouteChangeListener;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = GatewayCoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Phase 12: Gateway Core LISTEN/NOTIFY Invalidation Integration Test")
class GatewayCoreListenNotifyIntegrationTest {

    private static PostgreSQLContainer<?> postgresContainer;
    private static String jdbcUrl;
    private static String r2dbcUrl;
    private static String username;
    private static String password;
    private static boolean postgresAvailable = false;

    private static final UUID TENANT_ID = UUID.fromString("12121212-1212-1212-1212-121212121212");
    private static final UUID SERVICE_ID = UUID.fromString("34343434-3434-3434-3434-343434343434");

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

        Assumptions.assumeTrue(postgresAvailable, "PostgreSQL is required for LISTEN/NOTIFY integration testing");

        // Apply Flyway migrations including V6 triggers
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .ignoreMigrationPatterns("*:missing")
                .load();
        flyway.migrate();

        // Seed initial tenant and service
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO tenants (id, name, tenant_id, api_key, status) VALUES (?, ?, ?, ?, ?) " +
                            "ON CONFLICT (id) DO NOTHING")) {
                stmt.setObject(1, TENANT_ID);
                stmt.setString(2, "listen-notify-tenant");
                stmt.setObject(3, TENANT_ID);
                stmt.setString(4, "api-key-" + TENANT_ID);
                stmt.setString(5, "ACTIVE");
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO services (id, tenant_id, name, url) VALUES (?, ?, ?, ?) " +
                            "ON CONFLICT (id) DO NOTHING")) {
                stmt.setObject(1, SERVICE_ID);
                stmt.setObject(2, TENANT_ID);
                stmt.setString(3, "listen-service");
                stmt.setString(4, "http://mock-upstream:8080");
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
            registry.add("gateway.listener.enabled", () -> "true");
        }
    }

    @Autowired
    private RouteCache routeCache;

    @Autowired
    private RouteChangeListener routeChangeListener;

    @Test
    @DisplayName("INSERT on routes table notifies RouteChangeListener and populates RouteCache within 1 second")
    void testLiveRouteInsertNotification() throws SQLException {
        assertThat(routeChangeListener.isRunning()).isTrue();

        UUID routeId = UUID.randomUUID();
        String path = "/live-insert-" + UUID.randomUUID().toString().substring(0, 8);

        // Direct SQL INSERT into routes table
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO routes (id, tenant_id, service_id, name, paths, strip_path) VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setObject(1, routeId);
            stmt.setObject(2, TENANT_ID);
            stmt.setObject(3, SERVICE_ID);
            stmt.setString(4, "live-route");
            stmt.setString(5, path);
            stmt.setBoolean(6, true);
            stmt.executeUpdate();
        }

        // Within 1 second, RouteCache must reflect the new route without gateway restart
        await().atMost(Duration.ofMillis(1200))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {
                    Optional<RouteEntry> entry = routeCache.lookup(path, TENANT_ID);
                    assertThat(entry).isPresent();
                    assertThat(entry.get().routeId()).isEqualTo(routeId);
                    assertThat(entry.get().upstreamUrl()).isEqualTo("http://mock-upstream:8080");
                });
    }

    @Test
    @DisplayName("UPDATE on routes path invalidates old path and loads new path in RouteCache within 1 second")
    void testLiveRouteUpdateNotification() throws SQLException {
        UUID routeId = UUID.randomUUID();
        String originalPath = "/update-orig-" + UUID.randomUUID().toString().substring(0, 8);
        String updatedPath = "/update-new-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Insert route
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO routes (id, tenant_id, service_id, name, paths, strip_path) VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setObject(1, routeId);
            stmt.setObject(2, TENANT_ID);
            stmt.setObject(3, SERVICE_ID);
            stmt.setString(4, "updatable-route");
            stmt.setString(5, originalPath);
            stmt.setBoolean(6, true);
            stmt.executeUpdate();
        }

        // Wait for route to enter cache
        await().atMost(Duration.ofMillis(1200))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> routeCache.lookup(originalPath, TENANT_ID).isPresent());

        // 2. Update route path
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE routes SET paths = ? WHERE id = ?")) {
            stmt.setString(1, updatedPath);
            stmt.setObject(2, routeId);
            stmt.executeUpdate();
        }

        // Within 1 second: old path must be gone, new path must be present
        await().atMost(Duration.ofMillis(1200))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {
                    assertThat(routeCache.lookup(originalPath, TENANT_ID)).isEmpty();
                    Optional<RouteEntry> newRoute = routeCache.lookup(updatedPath, TENANT_ID);
                    assertThat(newRoute).isPresent();
                    assertThat(newRoute.get().routeId()).isEqualTo(routeId);
                });
    }

    @Test
    @DisplayName("DELETE on routes evicts route from RouteCache within 1 second")
    void testLiveRouteDeleteNotification() throws SQLException {
        UUID routeId = UUID.randomUUID();
        String path = "/delete-me-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Insert route
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO routes (id, tenant_id, service_id, name, paths, strip_path) VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setObject(1, routeId);
            stmt.setObject(2, TENANT_ID);
            stmt.setObject(3, SERVICE_ID);
            stmt.setString(4, "deletable-route");
            stmt.setString(5, path);
            stmt.setBoolean(6, true);
            stmt.executeUpdate();
        }

        // Verify route enters cache
        await().atMost(Duration.ofMillis(1200))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> routeCache.lookup(path, TENANT_ID).isPresent());

        // 2. Delete route
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM routes WHERE id = ?")) {
            stmt.setObject(1, routeId);
            stmt.executeUpdate();
        }

        // Within 1 second, route must be evicted from RouteCache
        await().atMost(Duration.ofMillis(1200))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {
                    assertThat(routeCache.lookup(path, TENANT_ID)).isEmpty();
                });
    }

    @Test
    @DisplayName("UPDATE on services table refreshes upstream target for attached routes within 1 second")
    void testLiveServiceUpdateNotification() throws SQLException {
        UUID serviceId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        String path = "/service-test-" + UUID.randomUUID().toString().substring(0, 8);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO services (id, tenant_id, name, url) VALUES (?, ?, ?, ?)")) {
                stmt.setObject(1, serviceId);
                stmt.setObject(2, TENANT_ID);
                stmt.setString(3, "test-svc");
                stmt.setString(4, "http://initial-upstream:8080");
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO routes (id, tenant_id, service_id, name, paths, strip_path) VALUES (?, ?, ?, ?, ?, ?)")) {
                stmt.setObject(1, routeId);
                stmt.setObject(2, TENANT_ID);
                stmt.setObject(3, serviceId);
                stmt.setString(4, "test-route");
                stmt.setString(5, path);
                stmt.setBoolean(6, true);
                stmt.executeUpdate();
            }
        }

        // Wait for route to be loaded
        await().atMost(Duration.ofMillis(1200))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> routeCache.lookup(path, TENANT_ID).isPresent());

        // Update service URL
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE services SET url = ? WHERE id = ?")) {
            stmt.setString(1, "http://updated-upstream:9090");
            stmt.setObject(2, serviceId);
            stmt.executeUpdate();
        }

        // RouteCache must reflect updated upstream URL within 1 second
        await().atMost(Duration.ofMillis(1200))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {
                    Optional<RouteEntry> entry = routeCache.lookup(path, TENANT_ID);
                    assertThat(entry).isPresent();
                    assertThat(entry.get().upstreamUrl()).isEqualTo("http://updated-upstream:9090");
                });
    }
}
