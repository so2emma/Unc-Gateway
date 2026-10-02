package com.unc.gateway.core.listen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record parsed from a raw PostgreSQL NOTIFY payload.
 * <p>
 * Holds the source table name, affected row id, tenant_id, and operation type
 * ({@code INSERT}, {@code UPDATE}, or {@code DELETE}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RouteChangeEvent(
        @JsonProperty(value = "operation", required = true) String operation,
        @JsonProperty(value = "table", required = true) String table,
        @JsonProperty(value = "id", required = true) UUID id,
        @JsonProperty("tenant_id") UUID tenantId
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public RouteChangeEvent {
        Objects.requireNonNull(operation, "operation cannot be null");
        Objects.requireNonNull(table, "table cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
    }

    /**
     * Parses a raw JSON string from a PostgreSQL NOTIFY payload into a {@link RouteChangeEvent}.
     *
     * @param json the raw NOTIFY payload JSON
     * @return parsed RouteChangeEvent
     * @throws IllegalArgumentException if the payload is null, blank, malformed, or missing required fields
     */
    public static RouteChangeEvent fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Notification payload cannot be null or blank");
        }
        try {
            RouteChangeEvent event = OBJECT_MAPPER.readValue(json, RouteChangeEvent.class);
            if (event.operation() == null || event.table() == null || event.id() == null) {
                throw new IllegalArgumentException("Notification payload missing required fields: " + json);
            }
            return event;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse notification payload: " + json, e);
        }
    }

    public boolean isInsert() {
        return "INSERT".equalsIgnoreCase(operation);
    }

    public boolean isUpdate() {
        return "UPDATE".equalsIgnoreCase(operation);
    }

    public boolean isDelete() {
        return "DELETE".equalsIgnoreCase(operation);
    }

    public boolean isRoute() {
        return "routes".equalsIgnoreCase(table);
    }

    public boolean isService() {
        return "services".equalsIgnoreCase(table);
    }

    public boolean isPluginConfig() {
        return "plugin_configs".equalsIgnoreCase(table);
    }
}
