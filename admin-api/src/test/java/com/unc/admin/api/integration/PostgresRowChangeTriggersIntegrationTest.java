package com.unc.admin.api.integration;

import com.unc.admin.api.notify.NotificationChannels;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phase 11: Postgres Row-Change Triggers and LISTEN/NOTIFY Integration Test")
class PostgresRowChangeTriggersIntegrationTest {

    private static PostgreSQLContainer<?> postgresContainer;
    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static boolean postgresAvailable = false;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();

    @BeforeAll
    static void setUp() {
        try {
            postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("unc_db")
                    .withUsername("postgres")
                    .withPassword("postgrespassword");
            postgresContainer.start();
            jdbcUrl = postgresContainer.getJdbcUrl();
            username = postgresContainer.getUsername();
            password = postgresContainer.getPassword();
            postgresAvailable = true;
        } catch (Throwable t) {
            // Testcontainers not available; check if running Postgres instance (e.g. Docker Compose unc-postgres) is reachable
            String localUrl = "jdbc:postgresql://localhost:5435/unc_db";
            try (Connection conn = DriverManager.getConnection(localUrl, "postgres", "postgrespassword")) {
                jdbcUrl = localUrl;
                username = "postgres";
                password = "postgrespassword";
                postgresAvailable = true;
            } catch (SQLException ignored) {
                postgresAvailable = false;
            }
        }

        Assumptions.assumeTrue(postgresAvailable, "A PostgreSQL instance is required for this integration test");

        // Run Flyway migrations through Phase 11
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // Verify V6 migration was applied
        boolean v6Applied = false;
        for (MigrationInfo info : flyway.info().applied()) {
            if ("6".equals(info.getVersion().getVersion())) {
                v6Applied = true;
                break;
            }
        }
        assertThat(v6Applied).as("V6__add_row_change_triggers.sql should be applied").isTrue();

        // Seed parent tenant and service
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO tenants (id, name, tenant_id, api_key, status) VALUES (?, ?, ?, ?, ?) " +
                            "ON CONFLICT (id) DO NOTHING")) {
                stmt.setObject(1, TENANT_ID);
                stmt.setString(2, "integration-tenant");
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
                stmt.setString(3, "parent-test-service");
                stmt.setString(4, "http://localhost:8080");
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to seed initial tenant/service", e);
        }
    }

    @AfterAll
    static void tearDown() {
        if (postgresAvailable) {
            try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM routes WHERE tenant_id = '" + TENANT_ID + "'");
                stmt.execute("DELETE FROM plugin_configs WHERE tenant_id = '" + TENANT_ID + "'");
                stmt.execute("DELETE FROM services WHERE tenant_id = '" + TENANT_ID + "'");
                stmt.execute("DELETE FROM tenants WHERE id = '" + TENANT_ID + "'");
            } catch (SQLException ignored) {
            }
        }
        if (postgresContainer != null && postgresContainer.isRunning()) {
            postgresContainer.stop();
        }
    }

    @Test
    @DisplayName("INSERT on services emits pg_notify on services_changed with INSERT operation and row id")
    void testServicesInsertNotification() throws Exception {
        UUID newServiceId = UUID.randomUUID();

        try (Connection listenConn = DriverManager.getConnection(jdbcUrl, username, password);
             Connection writeConn = DriverManager.getConnection(jdbcUrl, username, password)) {

            try (Statement stmt = listenConn.createStatement()) {
                stmt.execute("LISTEN " + NotificationChannels.SERVICES_CHANGED + ";");
            }

            PGConnection pgConn = listenConn.unwrap(PGConnection.class);

            // INSERT into services
            try (PreparedStatement stmt = writeConn.prepareStatement(
                    "INSERT INTO services (id, tenant_id, name, url) VALUES (?, ?, ?, ?)")) {
                stmt.setObject(1, newServiceId);
                stmt.setObject(2, TENANT_ID);
                stmt.setString(3, "test-notify-service");
                stmt.setString(4, "http://mock-upstream:9090");
                stmt.executeUpdate();
            }

            List<PGNotification> notifications = pollNotifications(pgConn, 3000);
            assertThat(notifications).isNotEmpty();

            PGNotification notification = notifications.stream()
                    .filter(n -> NotificationChannels.SERVICES_CHANGED.equals(n.getName()))
                    .filter(n -> n.getParameter().contains(newServiceId.toString()))
                    .findFirst()
                    .orElse(null);

            assertThat(notification).isNotNull();
            NotificationChannels.Payload payload = NotificationChannels.Payload.fromJson(notification.getParameter());
            assertThat(payload.operation()).isEqualTo(NotificationChannels.OP_INSERT);
            assertThat(payload.table()).isEqualTo(NotificationChannels.TABLE_SERVICES);
            assertThat(payload.id()).isEqualTo(newServiceId);
            assertThat(payload.tenantId()).isEqualTo(TENANT_ID);

            // Clean up
            try (PreparedStatement stmt = writeConn.prepareStatement("DELETE FROM services WHERE id = ?")) {
                stmt.setObject(1, newServiceId);
                stmt.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("INSERT, UPDATE, and DELETE on routes emit pg_notify on routes_changed matching operations")
    void testRoutesInsertUpdateDeleteNotifications() throws Exception {
        UUID routeId = UUID.randomUUID();

        try (Connection listenConn = DriverManager.getConnection(jdbcUrl, username, password);
             Connection writeConn = DriverManager.getConnection(jdbcUrl, username, password)) {

            try (Statement stmt = listenConn.createStatement()) {
                stmt.execute("LISTEN " + NotificationChannels.ROUTES_CHANGED + ";");
            }

            PGConnection pgConn = listenConn.unwrap(PGConnection.class);

            // 1. INSERT route
            try (PreparedStatement stmt = writeConn.prepareStatement(
                    "INSERT INTO routes (id, tenant_id, service_id, name, paths, strip_path) VALUES (?, ?, ?, ?, ?, ?)")) {
                stmt.setObject(1, routeId);
                stmt.setObject(2, TENANT_ID);
                stmt.setObject(3, SERVICE_ID);
                stmt.setString(4, "test-notify-route");
                stmt.setString(5, "/test-listen");
                stmt.setBoolean(6, true);
                stmt.executeUpdate();
            }

            List<PGNotification> insertNotifs = pollNotifications(pgConn, 3000);
            PGNotification insertNotif = insertNotifs.stream()
                    .filter(n -> NotificationChannels.ROUTES_CHANGED.equals(n.getName()))
                    .filter(n -> n.getParameter().contains(routeId.toString()))
                    .findFirst()
                    .orElse(null);

            assertThat(insertNotif).isNotNull();
            NotificationChannels.Payload insertPayload = NotificationChannels.Payload.fromJson(insertNotif.getParameter());
            assertThat(insertPayload.operation()).isEqualTo(NotificationChannels.OP_INSERT);
            assertThat(insertPayload.table()).isEqualTo(NotificationChannels.TABLE_ROUTES);
            assertThat(insertPayload.id()).isEqualTo(routeId);
            assertThat(insertPayload.tenantId()).isEqualTo(TENANT_ID);

            // 2. UPDATE route
            try (PreparedStatement stmt = writeConn.prepareStatement(
                    "UPDATE routes SET paths = ? WHERE id = ?")) {
                stmt.setString(1, "/test-listen-updated");
                stmt.setObject(2, routeId);
                stmt.executeUpdate();
            }

            List<PGNotification> updateNotifs = pollNotifications(pgConn, 3000);
            PGNotification updateNotif = updateNotifs.stream()
                    .filter(n -> NotificationChannels.ROUTES_CHANGED.equals(n.getName()))
                    .filter(n -> n.getParameter().contains(routeId.toString()))
                    .findFirst()
                    .orElse(null);

            assertThat(updateNotif).isNotNull();
            NotificationChannels.Payload updatePayload = NotificationChannels.Payload.fromJson(updateNotif.getParameter());
            assertThat(updatePayload.operation()).isEqualTo(NotificationChannels.OP_UPDATE);
            assertThat(updatePayload.table()).isEqualTo(NotificationChannels.TABLE_ROUTES);
            assertThat(updatePayload.id()).isEqualTo(routeId);
            assertThat(updatePayload.tenantId()).isEqualTo(TENANT_ID);

            // 3. DELETE route
            try (PreparedStatement stmt = writeConn.prepareStatement(
                    "DELETE FROM routes WHERE id = ?")) {
                stmt.setObject(1, routeId);
                stmt.executeUpdate();
            }

            List<PGNotification> deleteNotifs = pollNotifications(pgConn, 3000);
            PGNotification deleteNotif = deleteNotifs.stream()
                    .filter(n -> NotificationChannels.ROUTES_CHANGED.equals(n.getName()))
                    .filter(n -> n.getParameter().contains(routeId.toString()))
                    .findFirst()
                    .orElse(null);

            assertThat(deleteNotif).isNotNull();
            NotificationChannels.Payload deletePayload = NotificationChannels.Payload.fromJson(deleteNotif.getParameter());
            assertThat(deletePayload.operation()).isEqualTo(NotificationChannels.OP_DELETE);
            assertThat(deletePayload.table()).isEqualTo(NotificationChannels.TABLE_ROUTES);
            assertThat(deletePayload.id()).isEqualTo(routeId);
            assertThat(deletePayload.tenantId()).isEqualTo(TENANT_ID);
        }
    }

    @Test
    @DisplayName("INSERT on plugin_configs emits pg_notify on plugin_configs_changed")
    void testPluginConfigsInsertNotification() throws Exception {
        UUID configId = UUID.randomUUID();

        try (Connection listenConn = DriverManager.getConnection(jdbcUrl, username, password);
             Connection writeConn = DriverManager.getConnection(jdbcUrl, username, password)) {

            try (Statement stmt = listenConn.createStatement()) {
                stmt.execute("LISTEN " + NotificationChannels.PLUGIN_CONFIGS_CHANGED + ";");
            }

            PGConnection pgConn = listenConn.unwrap(PGConnection.class);

            // INSERT into plugin_configs
            try (PreparedStatement stmt = writeConn.prepareStatement(
                    "INSERT INTO plugin_configs (id, tenant_id, service_id, name, ordering, enabled, config) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)")) {
                stmt.setObject(1, configId);
                stmt.setObject(2, TENANT_ID);
                stmt.setObject(3, SERVICE_ID);
                stmt.setString(4, "key-auth");
                stmt.setInt(5, 1);
                stmt.setBoolean(6, true);
                stmt.setString(7, "{\"keyNames\": [\"apikey\"]}");
                stmt.executeUpdate();
            }

            List<PGNotification> notifications = pollNotifications(pgConn, 3000);
            PGNotification notification = notifications.stream()
                    .filter(n -> NotificationChannels.PLUGIN_CONFIGS_CHANGED.equals(n.getName()))
                    .filter(n -> n.getParameter().contains(configId.toString()))
                    .findFirst()
                    .orElse(null);

            assertThat(notification).isNotNull();
            NotificationChannels.Payload payload = NotificationChannels.Payload.fromJson(notification.getParameter());
            assertThat(payload.operation()).isEqualTo(NotificationChannels.OP_INSERT);
            assertThat(payload.table()).isEqualTo(NotificationChannels.TABLE_PLUGIN_CONFIGS);
            assertThat(payload.id()).isEqualTo(configId);
            assertThat(payload.tenantId()).isEqualTo(TENANT_ID);

            // Clean up
            try (PreparedStatement stmt = writeConn.prepareStatement("DELETE FROM plugin_configs WHERE id = ?")) {
                stmt.setObject(1, configId);
                stmt.executeUpdate();
            }
        }
    }

    private List<PGNotification> pollNotifications(PGConnection pgConnection, int timeoutMs) throws SQLException {
        List<PGNotification> result = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            PGNotification[] notifs = pgConnection.getNotifications(500);
            if (notifs != null && notifs.length > 0) {
                result.addAll(Arrays.asList(notifs));
                break;
            }
        }
        return result;
    }
}
