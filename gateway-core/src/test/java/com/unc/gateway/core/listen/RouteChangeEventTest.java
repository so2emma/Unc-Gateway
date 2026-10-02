package com.unc.gateway.core.listen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Phase 12: RouteChangeEvent Unit Tests")
class RouteChangeEventTest {

    @Test
    @DisplayName("correctly decodes raw NOTIFY JSON payload into typed RouteChangeEvent")
    void testParseValidPayload() {
        UUID rowId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String json = String.format("""
                {
                  "operation": "INSERT",
                  "table": "routes",
                  "id": "%s",
                  "tenant_id": "%s"
                }
                """, rowId, tenantId);

        RouteChangeEvent event = RouteChangeEvent.fromJson(json);

        assertThat(event.operation()).isEqualTo("INSERT");
        assertThat(event.table()).isEqualTo("routes");
        assertThat(event.id()).isEqualTo(rowId);
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.isInsert()).isTrue();
        assertThat(event.isUpdate()).isFalse();
        assertThat(event.isDelete()).isFalse();
        assertThat(event.isRoute()).isTrue();
        assertThat(event.isService()).isFalse();
        assertThat(event.isPluginConfig()).isFalse();
    }

    @Test
    @DisplayName("correctly decodes UPDATE and DELETE operations across different tables")
    void testParseUpdateAndDeletePayloads() {
        UUID serviceId = UUID.randomUUID();
        String updateJson = String.format("""
                {
                  "operation": "UPDATE",
                  "table": "services",
                  "id": "%s",
                  "tenant_id": null
                }
                """, serviceId);

        RouteChangeEvent updateEvent = RouteChangeEvent.fromJson(updateJson);
        assertThat(updateEvent.isUpdate()).isTrue();
        assertThat(updateEvent.isService()).isTrue();
        assertThat(updateEvent.id()).isEqualTo(serviceId);
        assertThat(updateEvent.tenantId()).isNull();

        UUID pluginId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String deleteJson = String.format("""
                {
                  "operation": "DELETE",
                  "table": "plugin_configs",
                  "id": "%s",
                  "tenant_id": "%s"
                }
                """, pluginId, tenantId);

        RouteChangeEvent deleteEvent = RouteChangeEvent.fromJson(deleteJson);
        assertThat(deleteEvent.isDelete()).isTrue();
        assertThat(deleteEvent.isPluginConfig()).isTrue();
        assertThat(deleteEvent.id()).isEqualTo(pluginId);
    }

    @Test
    @DisplayName("rejects malformed, empty, or missing required fields payloads with IllegalArgumentException")
    void testRejectsMalformedPayloads() {
        assertThatThrownBy(() -> RouteChangeEvent.fromJson(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> RouteChangeEvent.fromJson("   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> RouteChangeEvent.fromJson("not-json"))
                .isInstanceOf(IllegalArgumentException.class);

        // Missing id
        assertThatThrownBy(() -> RouteChangeEvent.fromJson("""
                {
                  "operation": "INSERT",
                  "table": "routes"
                }
                """)).isInstanceOf(IllegalArgumentException.class);

        // Missing operation
        assertThatThrownBy(() -> RouteChangeEvent.fromJson("""
                {
                  "table": "routes",
                  "id": "11111111-1111-1111-1111-111111111111"
                }
                """)).isInstanceOf(IllegalArgumentException.class);

        // Missing table
        assertThatThrownBy(() -> RouteChangeEvent.fromJson("""
                {
                  "operation": "INSERT",
                  "id": "11111111-1111-1111-1111-111111111111"
                }
                """)).isInstanceOf(IllegalArgumentException.class);
    }
}
