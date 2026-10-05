package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.PluginConfig;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class PluginConfigLoaderTest {

    @Test
    @DisplayName("PluginConfigLoader: loads enabled configs by tenantId")
    void testLoadEnabledConfigsByTenant() {
        DatabaseClient client = Mockito.mock(DatabaseClient.class);
        DatabaseClient.GenericExecuteSpec spec = Mockito.mock(DatabaseClient.GenericExecuteSpec.class);
        @SuppressWarnings("unchecked")
        FetchSpec<PluginConfig> fetchSpec = Mockito.mock(FetchSpec.class);

        UUID tenantId = UUID.randomUUID();
        PluginConfig config1 = new PluginConfig("1", tenantId.toString(), "key-auth", 1, true, Map.of());
        PluginConfig config2 = new PluginConfig("2", tenantId.toString(), "rate-limit", 2, true, Map.of("limit", 10));

        when(client.sql(anyString())).thenReturn(spec);
        when(spec.bind("tenantId", tenantId)).thenReturn(spec);
        when(spec.map(any(BiFunction.class))).thenReturn(fetchSpec);
        when(fetchSpec.all()).thenReturn(Flux.just(config1, config2));

        PluginConfigLoader loader = new PluginConfigLoader(client);

        StepVerifier.create(loader.loadEnabledConfigs(tenantId))
                .assertNext(list -> {
                    assertThat(list).hasSize(2);
                    assertThat(list.get(0).getName()).isEqualTo("key-auth");
                    assertThat(list.get(1).getName()).isEqualTo("rate-limit");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("PluginConfigLoader: mapRow converts JSON string to config map")
    void testMapRow() {
        Row row = Mockito.mock(Row.class);
        RowMetadata meta = Mockito.mock(RowMetadata.class);

        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(row.get("id", UUID.class)).thenReturn(id);
        when(row.get("tenant_id", UUID.class)).thenReturn(tenantId);
        when(row.get("name", String.class)).thenReturn("rate-limit");
        when(row.get("ordering", Integer.class)).thenReturn(5);
        when(row.get("enabled", Boolean.class)).thenReturn(true);
        when(row.get("config")).thenReturn("{\"limit\":100,\"window_seconds\":60}");

        PluginConfigLoader loader = new PluginConfigLoader(Mockito.mock(DatabaseClient.class));
        PluginConfig pluginConfig = loader.mapRow(row, meta);

        assertThat(pluginConfig.getId()).isEqualTo(id.toString());
        assertThat(pluginConfig.getTenantId()).isEqualTo(tenantId.toString());
        assertThat(pluginConfig.getName()).isEqualTo("rate-limit");
        assertThat(pluginConfig.getOrder()).isEqualTo(5);
        assertThat(pluginConfig.isEnabled()).isTrue();
        assertThat(pluginConfig.getConfig()).containsEntry("limit", 100);
        assertThat(pluginConfig.getConfig()).containsEntry("window_seconds", 60);
    }

    @Test
    @DisplayName("PluginConfigLoader: mapRow converts R2DBC Json object to config map")
    void testMapRowWithR2dbcJson() {
        Row row = Mockito.mock(Row.class);
        RowMetadata meta = Mockito.mock(RowMetadata.class);

        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(row.get("id", UUID.class)).thenReturn(id);
        when(row.get("tenant_id", UUID.class)).thenReturn(tenantId);
        when(row.get("name", String.class)).thenReturn("rate-limit");
        when(row.get("ordering", Integer.class)).thenReturn(2);
        when(row.get("enabled", Boolean.class)).thenReturn(true);
        when(row.get("config")).thenReturn(io.r2dbc.postgresql.codec.Json.of("{\"limit\":5,\"window_seconds\":60}"));

        PluginConfigLoader loader = new PluginConfigLoader(Mockito.mock(DatabaseClient.class));
        PluginConfig pluginConfig = loader.mapRow(row, meta);

        assertThat(pluginConfig.getId()).isEqualTo(id.toString());
        assertThat(pluginConfig.getTenantId()).isEqualTo(tenantId.toString());
        assertThat(pluginConfig.getName()).isEqualTo("rate-limit");
        assertThat(pluginConfig.getOrder()).isEqualTo(2);
        assertThat(pluginConfig.isEnabled()).isTrue();
        assertThat(pluginConfig.getConfig()).containsEntry("limit", 5);
        assertThat(pluginConfig.getConfig()).containsEntry("window_seconds", 60);
    }
}
