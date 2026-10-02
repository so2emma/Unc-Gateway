package com.unc.admin.api.notify;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Constants and typed payload representation for PostgreSQL LISTEN/NOTIFY row-change events.
 * <p>
 * Emitted by the {@code notify_row_change()} trigger function on mutations to {@code services},
 * {@code routes}, and {@code plugin_configs} tables.
 */
public final class NotificationChannels {

    public static final String SERVICES_CHANGED = "services_changed";
    public static final String ROUTES_CHANGED = "routes_changed";
    public static final String PLUGIN_CONFIGS_CHANGED = "plugin_configs_changed";

    public static final Set<String> ALL_CHANNELS = Set.of(
            SERVICES_CHANGED,
            ROUTES_CHANGED,
            PLUGIN_CONFIGS_CHANGED
    );

    public static final String TABLE_SERVICES = "services";
    public static final String TABLE_ROUTES = "routes";
    public static final String TABLE_PLUGIN_CONFIGS = "plugin_configs";

    public static final String OP_INSERT = "INSERT";
    public static final String OP_UPDATE = "UPDATE";
    public static final String OP_DELETE = "DELETE";

    public static final String FIELD_OPERATION = "operation";
    public static final String FIELD_TABLE = "table";
    public static final String FIELD_ID = "id";
    public static final String FIELD_TENANT_ID = "tenant_id";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private NotificationChannels() {
        // utility class
    }

    /**
     * Resolves the notification channel name for a given table name.
     *
     * @param table database table name (e.g. "services", "routes", "plugin_configs")
     * @return corresponding channel name (e.g. "services_changed")
     * @throws IllegalArgumentException if the table is unknown or unsupported
     */
    public static String channelForTable(String table) {
        Objects.requireNonNull(table, "table must not be null");
        return switch (table.toLowerCase()) {
            case TABLE_SERVICES -> SERVICES_CHANGED;
            case TABLE_ROUTES -> ROUTES_CHANGED;
            case TABLE_PLUGIN_CONFIGS -> PLUGIN_CONFIGS_CHANGED;
            default -> throw new IllegalArgumentException("Unsupported table for notification: " + table);
        };
    }

    /**
     * Immutable representation of the JSON notification payload emitted by the trigger.
     *
     * @param operation operation performed ("INSERT", "UPDATE", "DELETE")
     * @param table     table affected ("services", "routes", "plugin_configs")
     * @param id        primary key of the affected row
     * @param tenantId  tenant identifier associated with the affected row
     */
    public record Payload(
            @JsonProperty(FIELD_OPERATION) String operation,
            @JsonProperty(FIELD_TABLE) String table,
            @JsonProperty(FIELD_ID) UUID id,
            @JsonProperty(FIELD_TENANT_ID) UUID tenantId
    ) {
        public static Payload fromJson(String json) {
            try {
                return OBJECT_MAPPER.readValue(json, Payload.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse notification payload JSON: " + json, e);
            }
        }

        public String toJson() {
            try {
                return OBJECT_MAPPER.writeValueAsString(this);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize notification payload", e);
            }
        }
    }
}
