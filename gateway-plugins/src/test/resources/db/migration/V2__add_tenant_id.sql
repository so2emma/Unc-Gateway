-- V2__add_tenant_id.sql: Multi-tenant schema migration adding UUID tenant_id column across all tables

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE tenants SET tenant_id = id WHERE tenant_id IS NULL;

ALTER TABLE services ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE routes ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE consumers ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE plugin_configs ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE request_logs ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_tenants_tenant_id ON tenants(tenant_id);
CREATE INDEX IF NOT EXISTS idx_services_tenant_id ON services(tenant_id);
CREATE INDEX IF NOT EXISTS idx_routes_tenant_id ON routes(tenant_id);
CREATE INDEX IF NOT EXISTS idx_consumers_tenant_id ON consumers(tenant_id);
CREATE INDEX IF NOT EXISTS idx_plugin_configs_tenant_id ON plugin_configs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_request_logs_tenant_id ON request_logs(tenant_id);

CREATE INDEX IF NOT EXISTS idx_services_tenant_id_id ON services(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_routes_tenant_id_id ON routes(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_consumers_tenant_id_id ON consumers(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_plugin_configs_tenant_id_id ON plugin_configs(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_request_logs_tenant_id_id ON request_logs(tenant_id, id);
