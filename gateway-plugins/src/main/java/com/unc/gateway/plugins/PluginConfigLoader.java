package com.unc.gateway.plugins;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unc.gateway.plugins.api.PluginConfig;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Loads enabled, tenant-scoped plugin configuration records from PostgreSQL via R2DBC.
 */
@Component
public class PluginConfigLoader {

    static final String SELECT_ENABLED_BY_TENANT_SQL = """
            SELECT id, tenant_id, service_id, route_id, consumer_id, name, ordering, enabled, config
            FROM plugin_configs
            WHERE tenant_id = :tenantId AND enabled = TRUE
            ORDER BY ordering ASC
            """;

    static final String SELECT_ALL_ENABLED_SQL = """
            SELECT id, tenant_id, service_id, route_id, consumer_id, name, ordering, enabled, config
            FROM plugin_configs
            WHERE enabled = TRUE
            ORDER BY ordering ASC
            """;

    static final String SELECT_ENABLED_BY_TENANT_AND_NAME_SQL = """
            SELECT id, tenant_id, service_id, route_id, consumer_id, name, ordering, enabled, config
            FROM plugin_configs
            WHERE tenant_id = :tenantId AND name = :name AND enabled = TRUE
            ORDER BY ordering ASC
            LIMIT 1
            """;

    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    public PluginConfigLoader(DatabaseClient databaseClient) {
        this(databaseClient, new ObjectMapper());
    }

    public PluginConfigLoader(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Loads all enabled plugin configurations scoped to a specific tenant.
     *
     * @param tenantId tenant identifier
     * @return {@link Mono} emitting the list of active {@link PluginConfig}s ordered by order ascending
     */
    public Mono<List<PluginConfig>> loadEnabledConfigs(UUID tenantId) {
        if (tenantId == null) {
            return loadAllEnabledConfigs();
        }
        return databaseClient.sql(SELECT_ENABLED_BY_TENANT_SQL)
                .bind("tenantId", tenantId)
                .map(this::mapRow)
                .all()
                .collectList();
    }

    /**
     * Loads all enabled plugin configurations across all tenants.
     *
     * @return {@link Mono} emitting the list of all active {@link PluginConfig}s
     */
    public Mono<List<PluginConfig>> loadAllEnabledConfigs() {
        return databaseClient.sql(SELECT_ALL_ENABLED_SQL)
                .map(this::mapRow)
                .all()
                .collectList();
    }

    /**
     * Loads a specific enabled plugin configuration for a tenant by plugin name.
     *
     * @param tenantId   tenant identifier
     * @param pluginName name of the plugin (e.g. "key-auth", "rate-limit")
     * @return {@link Mono} emitting the active {@link PluginConfig}, or empty
     */
    public Mono<PluginConfig> loadPluginConfig(UUID tenantId, String pluginName) {
        if (tenantId == null || pluginName == null || pluginName.isBlank()) {
            return Mono.empty();
        }
        return databaseClient.sql(SELECT_ENABLED_BY_TENANT_AND_NAME_SQL)
                .bind("tenantId", tenantId)
                .bind("name", pluginName.trim())
                .map(this::mapRow)
                .one();
    }

    PluginConfig mapRow(Row row, RowMetadata metadata) {
        UUID id = row.get("id", UUID.class);
        UUID tenantId = row.get("tenant_id", UUID.class);
        String name = row.get("name", String.class);
        Integer ordering = row.get("ordering", Integer.class);
        Boolean enabled = row.get("enabled", Boolean.class);
        Object rawConfig = row.get("config");

        Map<String, Object> configMap = parseConfig(rawConfig);

        return new PluginConfig(
                id != null ? id.toString() : null,
                tenantId != null ? tenantId.toString() : null,
                name,
                ordering != null ? ordering : 0,
                enabled == null || enabled,
                configMap
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(Object configObj) {
        if (configObj == null) {
            return Collections.emptyMap();
        }
        if (configObj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        String str = configObj.toString();
        if (str.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(str, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }
}
