package com.unc.admin.api.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class V1BaseSchemaMigrationTest {

    private static final String H2_URL = "jdbc:h2:mem:unc_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    @Test
    @DisplayName("V1__base_schema.sql - should apply cleanly and create all six base tables")
    void testV1BaseSchemaMigration() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(H2_URL, USER, PASSWORD)
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        try (Connection conn = DriverManager.getConnection(H2_URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(
                    "SELECT table_name FROM information_schema.tables"
            );

            Set<String> tables = new HashSet<>();
            while (rs.next()) {
                tables.add(rs.getString("table_name").toLowerCase());
            }

            assertThat(tables).contains(
                    "tenants",
                    "services",
                    "routes",
                    "consumers",
                    "plugin_configs",
                    "request_logs",
                    "flyway_schema_history"
            );
        }
    }
}
