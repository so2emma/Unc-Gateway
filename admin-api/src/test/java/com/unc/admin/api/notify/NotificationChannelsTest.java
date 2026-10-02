package com.unc.admin.api.notify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NotificationChannels unit tests")
class NotificationChannelsTest {

    @Test
    @DisplayName("NotificationChannels constants match contract values")
    void testConstants() {
        assertThat(NotificationChannels.SERVICES_CHANGED).isEqualTo("services_changed");
        assertThat(NotificationChannels.ROUTES_CHANGED).isEqualTo("routes_changed");
        assertThat(NotificationChannels.PLUGIN_CONFIGS_CHANGED).isEqualTo("plugin_configs_changed");

        assertThat(NotificationChannels.ALL_CHANNELS).containsExactlyInAnyOrder(
                "services_changed",
                "routes_changed",
                "plugin_configs_changed"
        );

        assertThat(NotificationChannels.TABLE_SERVICES).isEqualTo("services");
        assertThat(NotificationChannels.TABLE_ROUTES).isEqualTo("routes");
        assertThat(NotificationChannels.TABLE_PLUGIN_CONFIGS).isEqualTo("plugin_configs");

        assertThat(NotificationChannels.OP_INSERT).isEqualTo("INSERT");
        assertThat(NotificationChannels.OP_UPDATE).isEqualTo("UPDATE");
        assertThat(NotificationChannels.OP_DELETE).isEqualTo("DELETE");

        assertThat(NotificationChannels.FIELD_OPERATION).isEqualTo("operation");
        assertThat(NotificationChannels.FIELD_TABLE).isEqualTo("table");
        assertThat(NotificationChannels.FIELD_ID).isEqualTo("id");
        assertThat(NotificationChannels.FIELD_TENANT_ID).isEqualTo("tenant_id");
    }

    @Test
    @DisplayName("channelForTable resolves correctly for valid tables and rejects invalid")
    void testChannelForTable() {
        assertThat(NotificationChannels.channelForTable("services")).isEqualTo("services_changed");
        assertThat(NotificationChannels.channelForTable("routes")).isEqualTo("routes_changed");
        assertThat(NotificationChannels.channelForTable("plugin_configs")).isEqualTo("plugin_configs_changed");

        // Case insensitivity
        assertThat(NotificationChannels.channelForTable("SERVICES")).isEqualTo("services_changed");
        assertThat(NotificationChannels.channelForTable("Routes")).isEqualTo("routes_changed");

        assertThatThrownBy(() -> NotificationChannels.channelForTable("unknown_table"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported table");

        assertThatThrownBy(() -> NotificationChannels.channelForTable(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Payload serialization and deserialization match JSON contract")
    void testPayloadJsonContract() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        NotificationChannels.Payload payload = new NotificationChannels.Payload(
                NotificationChannels.OP_INSERT,
                NotificationChannels.TABLE_ROUTES,
                id,
                tenantId
        );

        String json = payload.toJson();
        assertThat(json).contains("\"operation\":\"INSERT\"");
        assertThat(json).contains("\"table\":\"routes\"");
        assertThat(json).contains("\"id\":\"" + id + "\"");
        assertThat(json).contains("\"tenant_id\":\"" + tenantId + "\"");

        NotificationChannels.Payload parsed = NotificationChannels.Payload.fromJson(json);
        assertThat(parsed.operation()).isEqualTo(NotificationChannels.OP_INSERT);
        assertThat(parsed.table()).isEqualTo(NotificationChannels.TABLE_ROUTES);
        assertThat(parsed.id()).isEqualTo(id);
        assertThat(parsed.tenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("V6 migration file contains literal channel conventions, trigger definitions, and payload fields")
    void testMigrationSqlConsistency() throws Exception {
        String migrationPath = "/db/migration/V6__add_row_change_triggers.sql";
        try (InputStream is = getClass().getResourceAsStream(migrationPath)) {
            assertThat(is).as("Migration file %s must exist on classpath", migrationPath).isNotNull();
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Channel resolution contract in SQL
            assertThat(sql).contains("channel_name := TG_TABLE_NAME || '_changed';");
            for (String table : new String[]{"services", "routes", "plugin_configs"}) {
                String expectedChannel = table + "_changed";
                assertThat(NotificationChannels.channelForTable(table)).isEqualTo(expectedChannel);
            }

            // Function and triggers
            assertThat(sql).contains("CREATE OR REPLACE FUNCTION notify_row_change()");
            assertThat(sql).contains("LANGUAGE plpgsql");
            assertThat(sql).contains("PERFORM pg_notify(channel_name, payload);");

            assertThat(sql).contains("CREATE TRIGGER services_notify_trigger");
            assertThat(sql).contains("AFTER INSERT OR UPDATE OR DELETE ON services");
            assertThat(sql).contains("FOR EACH ROW");

            assertThat(sql).contains("CREATE TRIGGER routes_notify_trigger");
            assertThat(sql).contains("AFTER INSERT OR UPDATE OR DELETE ON routes");

            assertThat(sql).contains("CREATE TRIGGER plugin_configs_notify_trigger");
            assertThat(sql).contains("AFTER INSERT OR UPDATE OR DELETE ON plugin_configs");

            // JSON fields in payload
            assertThat(sql).contains("'operation', TG_OP");
            assertThat(sql).contains("'table', TG_TABLE_NAME");
            assertThat(sql).contains("'id', record_id");
            assertThat(sql).contains("'tenant_id', record_tenant_id");
        }
    }
}
