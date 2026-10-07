package com.unc.gateway.plugins.integration;

import com.unc.gateway.plugins.ConsumerIdentity;
import com.unc.gateway.plugins.ConsumerKeyLookup;
import com.unc.gateway.plugins.PluginConfigLoader;
import com.unc.gateway.plugins.api.PluginConfig;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class KeyAuthPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("unc_db")
            .withUsername("postgres")
            .withPassword("postgrespassword");

    private static ConsumerKeyLookup consumerKeyLookup;
    private static PluginConfigLoader pluginConfigLoader;

    private static final UUID T1 = UUID.randomUUID();
    private static final UUID T2 = UUID.randomUUID();
    private static final UUID C1 = UUID.randomUUID();
    private static final UUID C2 = UUID.randomUUID();
    private static final UUID KEY1_ID = UUID.randomUUID();
    private static final UUID KEY2_ID = UUID.randomUUID();
    private static final UUID KEY3_ID = UUID.randomUUID();

    private static final String RAW_KEY1 = "acme-secret-key-1";
    private static final String RAW_KEY2_REVOKED = "acme-revoked-key-2";
    private static final String RAW_KEY3_T2 = "other-secret-key-3";

    @BeforeAll
    static void setUp() throws SQLException {
        // Run Flyway migrations against the PostgreSQL container
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // Seed data via JDBC
        seedTestData();

        // Initialize R2DBC client and components
        PostgresqlConnectionConfiguration config = PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getFirstMappedPort())
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build();
        PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(config);
        DatabaseClient databaseClient = DatabaseClient.create(connectionFactory);

        consumerKeyLookup = new ConsumerKeyLookup(databaseClient);
        pluginConfigLoader = new PluginConfigLoader(databaseClient);
    }

    private static void seedTestData() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {

            // Tenants
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO tenants (id, name) VALUES (?, ?)")) {
                stmt.setObject(1, T1);
                stmt.setString(2, "tenant-acme");
                stmt.executeUpdate();

                stmt.setObject(1, T2);
                stmt.setString(2, "tenant-other");
                stmt.executeUpdate();
            }

            // Consumers
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO consumers (id, tenant_id, username) VALUES (?, ?, ?)")) {
                stmt.setObject(1, C1);
                stmt.setObject(2, T1);
                stmt.setString(3, "acme-consumer");
                stmt.executeUpdate();

                stmt.setObject(1, C2);
                stmt.setObject(2, T2);
                stmt.setString(3, "other-consumer");
                stmt.executeUpdate();
            }

            // Consumer Keys
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO consumer_keys (id, tenant_id, consumer_id, name, key_prefix, key_hash, status) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                // Key 1: Active key under T1 / C1
                stmt.setObject(1, KEY1_ID);
                stmt.setObject(2, T1);
                stmt.setObject(3, C1);
                stmt.setString(4, "key-primary");
                stmt.setString(5, "acme-sec");
                stmt.setString(6, ConsumerKeyLookup.sha256Hex(RAW_KEY1));
                stmt.setString(7, "ACTIVE");
                stmt.executeUpdate();

                // Key 2: Revoked key under T1 / C1
                stmt.setObject(1, KEY2_ID);
                stmt.setObject(2, T1);
                stmt.setObject(3, C1);
                stmt.setString(4, "key-revoked");
                stmt.setString(5, "acme-rev");
                stmt.setString(6, ConsumerKeyLookup.sha256Hex(RAW_KEY2_REVOKED));
                stmt.setString(7, "REVOKED");
                stmt.executeUpdate();

                // Key 3: Active key under T2 / C2
                stmt.setObject(1, KEY3_ID);
                stmt.setObject(2, T2);
                stmt.setObject(3, C2);
                stmt.setString(4, "key-other");
                stmt.setString(5, "other-se");
                stmt.setString(6, ConsumerKeyLookup.sha256Hex(RAW_KEY3_T2));
                stmt.setString(7, "ACTIVE");
                stmt.executeUpdate();
            }

            // Plugin configs under T1
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO plugin_configs (id, tenant_id, name, ordering, enabled, config) " +
                            "VALUES (?, ?, ?, ?, ?, ?::jsonb)")) {
                // key-auth (enabled, order 1)
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, T1);
                stmt.setString(3, "key-auth");
                stmt.setInt(4, 1);
                stmt.setBoolean(5, true);
                stmt.setString(6, "{}");
                stmt.executeUpdate();

                // rate-limit (enabled, order 2)
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, T1);
                stmt.setString(3, "rate-limit");
                stmt.setInt(4, 2);
                stmt.setBoolean(5, true);
                stmt.setString(6, "{\"limit\": 5, \"windowSeconds\": 10}");
                stmt.executeUpdate();

                // disabled-plugin (disabled, order 3)
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, T1);
                stmt.setString(3, "disabled-plugin");
                stmt.setInt(4, 3);
                stmt.setBoolean(5, false);
                stmt.setString(6, "{}");
                stmt.executeUpdate();

                // rate-limit under T2 (enabled, order 1)
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, T2);
                stmt.setString(3, "rate-limit");
                stmt.setInt(4, 1);
                stmt.setBoolean(5, true);
                stmt.setString(6, "{\"limit\": 100, \"windowSeconds\": 60}");
                stmt.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("ConsumerKeyLookup: resolves active raw API key to correct tenant and consumer identity")
    void testLookupValidKeyResolvesIdentity() {
        StepVerifier.create(consumerKeyLookup.lookup(RAW_KEY1))
                .assertNext(identity -> {
                    assertThat(identity.tenantId()).isEqualTo(T1);
                    assertThat(identity.consumerId()).isEqualTo(C1);
                    assertThat(identity.keyId()).isEqualTo(KEY1_ID);
                    assertThat(identity.keyName()).isEqualTo("key-primary");
                    assertThat(identity.username()).isEqualTo("acme-consumer");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("ConsumerKeyLookup: returns empty Mono for revoked key")
    void testLookupRevokedKeyReturnsEmpty() {
        StepVerifier.create(consumerKeyLookup.lookup(RAW_KEY2_REVOKED))
                .verifyComplete();
    }

    @Test
    @DisplayName("ConsumerKeyLookup: returns empty Mono for unknown key")
    void testLookupUnknownKeyReturnsEmpty() {
        StepVerifier.create(consumerKeyLookup.lookup("completely-unknown-key-999"))
                .verifyComplete();
    }

    @Test
    @DisplayName("ConsumerKeyLookup: key always resolves to its owning tenant regardless of context tenant")
    void testTenantIsolation_KeyAlwaysResolvesToOwningTenant() {
        // Look up T1's key passing T2 as context tenant -> must still resolve to T1
        StepVerifier.create(consumerKeyLookup.lookup(RAW_KEY1, T2))
                .assertNext(identity -> {
                    assertThat(identity.tenantId()).isEqualTo(T1);
                    assertThat(identity.consumerId()).isEqualTo(C1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("ConsumerKeyLookup: lookupByHash resolves active key directly")
    void testLookupByHashDirectly() {
        String hash = ConsumerKeyLookup.sha256Hex(RAW_KEY1);
        StepVerifier.create(consumerKeyLookup.lookupByHash(hash))
                .assertNext(identity -> {
                    assertThat(identity.tenantId()).isEqualTo(T1);
                    assertThat(identity.consumerId()).isEqualTo(C1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("PluginConfigLoader: reads enabled plugin configurations for tenant ordered by ordering ASC")
    void testPluginConfigLoaderReadsEnabledConfigsInOrder() {
        StepVerifier.create(pluginConfigLoader.loadEnabledConfigs(T1))
                .assertNext(configs -> {
                    assertThat(configs).hasSize(2);
                    assertThat(configs.get(0).getName()).isEqualTo("key-auth");
                    assertThat(configs.get(0).getOrder()).isEqualTo(1);
                    assertThat(configs.get(1).getName()).isEqualTo("rate-limit");
                    assertThat(configs.get(1).getOrder()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("PluginConfigLoader: reads specific plugin configuration for tenant")
    void testPluginConfigLoaderReadsSpecificPluginConfig() {
        StepVerifier.create(pluginConfigLoader.loadPluginConfig(T1, "rate-limit"))
                .assertNext(config -> {
                    assertThat(config.getName()).isEqualTo("rate-limit");
                    assertThat(config.isEnabled()).isTrue();
                    assertThat(config.getConfig()).containsEntry("limit", 5);
                    assertThat(config.getConfig()).containsEntry("windowSeconds", 10);
                })
                .verifyComplete();

        StepVerifier.create(pluginConfigLoader.loadPluginConfig(T1, "disabled-plugin"))
                .verifyComplete();
    }

    @Test
    @DisplayName("PluginConfigLoader: maintains tenant isolation for plugin configurations")
    void testPluginConfigLoaderTenantIsolation() {
        StepVerifier.create(pluginConfigLoader.loadEnabledConfigs(T2))
                .assertNext(configs -> {
                    assertThat(configs).hasSize(1);
                    PluginConfig config = configs.get(0);
                    assertThat(config.getName()).isEqualTo("rate-limit");
                    assertThat(config.getConfig()).containsEntry("limit", 100);
                    assertThat(config.getConfig()).containsEntry("windowSeconds", 60);
                })
                .verifyComplete();
    }
}
