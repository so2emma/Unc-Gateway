package com.unc.admin.api.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class V2MultiTenantSchemaMigrationTest {

    private static final String H2_URL = "jdbc:h2:mem:unc_db_v2;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    @Test
    @DisplayName("V2__add_tenant_id.sql - should apply V1 and V2 cleanly and add tenant_id to all six tables")
    void testV2MultiTenantSchemaMigration() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(H2_URL, USER, PASSWORD)
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        try (Connection conn = DriverManager.getConnection(H2_URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(
                    "SELECT table_name, column_name FROM information_schema.columns WHERE column_name = 'TENANT_ID' OR column_name = 'tenant_id'"
            );

            Map<String, String> tableColumnMap = new HashMap<>();
            while (rs.next()) {
                tableColumnMap.put(rs.getString("table_name").toLowerCase(), rs.getString("column_name").toLowerCase());
            }

            assertThat(tableColumnMap).containsKeys(
                    "tenants",
                    "services",
                    "routes",
                    "consumers",
                    "plugin_configs",
                    "request_logs"
            );

            for (String table : tableColumnMap.keySet()) {
                assertThat(tableColumnMap.get(table)).isEqualTo("tenant_id");
            }
        }
    }
}
