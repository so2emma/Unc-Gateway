-- V6__add_row_change_triggers.sql: PostgreSQL triggers and notification setup for services, routes, and plugin_configs

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

DROP TRIGGER IF EXISTS services_notify_trigger ON services;
CREATE TRIGGER services_notify_trigger
    AFTER INSERT OR UPDATE OR DELETE ON services
    FOR EACH ROW
    EXECUTE FUNCTION notify_row_change();

DROP TRIGGER IF EXISTS routes_notify_trigger ON routes;
CREATE TRIGGER routes_notify_trigger
    AFTER INSERT OR UPDATE OR DELETE ON routes
    FOR EACH ROW
    EXECUTE FUNCTION notify_row_change();

DROP TRIGGER IF EXISTS plugin_configs_notify_trigger ON plugin_configs;
CREATE TRIGGER plugin_configs_notify_trigger
    AFTER INSERT OR UPDATE OR DELETE ON plugin_configs
    FOR EACH ROW
    EXECUTE FUNCTION notify_row_change();
