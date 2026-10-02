# PostgreSQL LISTEN/NOTIFY Row-Change Payload Contract

## Overview

Unc Gateway uses PostgreSQL row-level triggers combined with `NOTIFY` / `pg_notify` to broadcast real-time data mutations from `admin-api` to downstream consumers such as `gateway-core`.

This mechanism enables immediate cache invalidation and targeted row reload in `gateway-core` within sub-second bounds without polling loops or manual cache flushes.

---

## Notification Channels

Each cached entity table emits to its own dedicated notification channel:

| Table | Notification Channel | Event Trigger |
| :--- | :--- | :--- |
| `services` | `services_changed` | `AFTER INSERT OR UPDATE OR DELETE` |
| `routes` | `routes_changed` | `AFTER INSERT OR UPDATE OR DELETE` |
| `plugin_configs` | `plugin_configs_changed` | `AFTER INSERT OR UPDATE OR DELETE` |

In Java, these channel constants are exposed via `com.unc.admin.api.notify.NotificationChannels`:
- `NotificationChannels.SERVICES_CHANGED` (`"services_changed"`)
- `NotificationChannels.ROUTES_CHANGED` (`"routes_changed"`)
- `NotificationChannels.PLUGIN_CONFIGS_CHANGED` (`"plugin_configs_changed"`)

---

## JSON Payload Specification

Every notification emitted across the above channels carries a single JSON string formatted as follows:

```json
{
  "operation": "INSERT",
  "table": "routes",
  "id": "33333333-3333-3333-3333-333333333333",
  "tenant_id": "11111111-1111-1111-1111-111111111111"
}
```

### Fields

| Field | Type | Description |
| :--- | :--- | :--- |
| `operation` | `String` | Type of mutation: `"INSERT"`, `"UPDATE"`, or `"DELETE"`. |
| `table` | `String` | Name of the table that changed: `"services"`, `"routes"`, or `"plugin_configs"`. |
| `id` | `UUID` (string) | Primary key (`id`) of the affected row. For `DELETE`, this is `OLD.id`; for `INSERT`/`UPDATE`, `NEW.id`. |
| `tenant_id` | `UUID` (string) | Multi-tenant identifier of the affected row. For `DELETE`, this is `OLD.tenant_id`; for `INSERT`/`UPDATE`, `NEW.tenant_id`. |

---

## Trigger Function Implementation

Defined in Flyway migration `V6__add_row_change_triggers.sql`:

```sql
CREATE OR REPLACE FUNCTION notify_row_change()
RETURNS trigger AS $$
DECLARE
    record_id UUID;
    record_tenant_id UUID;
    channel_name TEXT;
    payload TEXT;
BEGIN
    IF (TG_OP = 'DELETE') THEN
        record_id := OLD.id;
        record_tenant_id := OLD.tenant_id;
    ELSE
        record_id := NEW.id;
        record_tenant_id := NEW.tenant_id;
    END IF;

    channel_name := TG_TABLE_NAME || '_changed';

    payload := json_build_object(
        'operation', TG_OP,
        'table', TG_TABLE_NAME,
        'id', record_id,
        'tenant_id', record_tenant_id
    )::text;

    PERFORM pg_notify(channel_name, payload);

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
```

### Attached Triggers

- `services_notify_trigger` on `services`
- `routes_notify_trigger` on `routes`
- `plugin_configs_notify_trigger` on `plugin_configs`

All triggers execute `AFTER INSERT OR UPDATE OR DELETE FOR EACH ROW`.

---

## Example Payloads

### 1. Route Created (`INSERT`)
```json
{
  "operation": "INSERT",
  "table": "routes",
  "id": "e296a93d-cb45-4c52-9a01-c265d994d8c0",
  "tenant_id": "72e236cd-94c8-4880-b06d-7ee1ecf8e417"
}
```

### 2. Service Updated (`UPDATE`)
```json
{
  "operation": "UPDATE",
  "table": "services",
  "id": "6a9f4c3b-2856-4351-8789-9a7061d43eb4",
  "tenant_id": "72e236cd-94c8-4880-b06d-7ee1ecf8e417"
}
```

### 3. Plugin Config Deleted (`DELETE`)
```json
{
  "operation": "DELETE",
  "table": "plugin_configs",
  "id": "fb58cebb-1983-4876-96b6-a94f6f9c8ef7",
  "tenant_id": "72e236cd-94c8-4880-b06d-7ee1ecf8e417"
}
```

---

## Consumer Implementation Guidelines (Phase 12)

1. **Connection**: Consumers must open a dedicated, long-lived PostgreSQL connection (e.g. via R2DBC or PGConnection) and issue `LISTEN services_changed; LISTEN routes_changed; LISTEN plugin_configs_changed;`.
2. **Payload Parsing**: Payloads can be parsed into strongly typed events (e.g. `NotificationChannels.Payload` or Phase 12 `RouteChangeEvent`).
3. **Targeted Invalidation**:
   - On `INSERT` or `UPDATE`: Query only the affected row by `id` and `tenant_id`, and update the in-memory cache.
   - On `DELETE`: Evict the row from cache by `id` and `tenant_id`.
4. **Reconnection**: In the event of a dropped connection or network partition, consumers must re-establish the connection, re-issue `LISTEN`, and perform a reconciliation pass (Phase 15).
