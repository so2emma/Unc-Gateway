-- V3__tenant_auth_and_crud.sql: Add tenant authentication credentials and management status

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS api_key VARCHAR(255) UNIQUE;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'ACTIVE';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS email VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_tenants_api_key ON tenants(api_key);
CREATE INDEX IF NOT EXISTS idx_tenants_id_api_key ON tenants(id, api_key);
