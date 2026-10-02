package com.unc.admin.api.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V4ConsumerKeysMigrationTest {

    private static final String H2_URL =
            "jdbc:h2:mem:unc_db_v4;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    @Test
    @DisplayName("V4/V5 - creates consumer_keys, adds consumer profile columns, and makes username unique per tenant")
    void testV4AndV5Migrations() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(H2_URL, USER, PASSWORD)
                .locations("classpath:db/migration")
                .target("5")
                .load();

        flyway.migrate();

        try (Connection conn = DriverManager.getConnection(H2_URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            assertThat(columnsOf(stmt, "consumer_keys")).contains(
                    "id", "tenant_id", "consumer_id", "name", "key_prefix", "key_hash",
                    "status", "created_at", "updated_at", "revoked_at");

            assertThat(columnsOf(stmt, "consumers")).contains("email", "organization");

            UUID tenantA = UUID.randomUUID();
            UUID tenantB = UUID.randomUUID();
            seedTenant(stmt, tenantA, "tenant-a");
            seedTenant(stmt, tenantB, "tenant-b");

            // The same consumer username is allowed once per tenant...
            assertThatCode(() -> insertConsumer(stmt, tenantA, "shared-name")).doesNotThrowAnyException();
            assertThatCode(() -> insertConsumer(stmt, tenantB, "shared-name")).doesNotThrowAnyException();

            // ...but not twice within the same tenant.
            assertThatThrownBy(() -> insertConsumer(stmt, tenantA, "shared-name"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private Set<String> columnsOf(Statement stmt, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet rs = stmt.executeQuery(
                "SELECT column_name FROM information_schema.columns WHERE LOWER(table_name) = '" + table + "'")) {
            while (rs.next()) {
                columns.add(rs.getString("column_name").toLowerCase());
            }
        }
        return columns;
    }

    private void seedTenant(Statement stmt, UUID id, String name) throws SQLException {
        stmt.execute("INSERT INTO tenants (id, tenant_id, name, api_key, status) VALUES ('"
                + id + "', '" + id + "', '" + name + "', 'key-" + id + "', 'ACTIVE')");
    }

    private void insertConsumer(Statement stmt, UUID tenantId, String username) throws SQLException {
        stmt.execute("INSERT INTO consumers (id, tenant_id, username) VALUES ('"
                + UUID.randomUUID() + "', '" + tenantId + "', '" + username + "')");
    }
}
