-- V4__consumer_keys_and_consumer_profile.sql
-- Phase 8: consumer self-serve profile fields, per-tenant username uniqueness, and API key issuance table.

-- Self-serve signup profile fields (developer-portal signup form: name / email / organization).
ALTER TABLE consumers ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE consumers ADD COLUMN IF NOT EXISTS organization VARCHAR(255);

-- V1 declared consumers.username globally UNIQUE, which is incorrect under the shared-schema
-- pool multi-tenancy model: two distinct tenants must be able to own the same consumer username.
-- Add the per-tenant uniqueness guarantee here; the now-redundant global constraint is dropped by
-- V5, which has to resolve the constraint's generated name from the catalog.
CREATE UNIQUE INDEX IF NOT EXISTS uq_consumers_tenant_id_username ON consumers(tenant_id, username);

-- API keys issued to a consumer. Only the hash of the key material is persisted; the raw key is
-- returned exactly once, in the issuance response.
CREATE TABLE IF NOT EXISTS consumer_keys (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    consumer_id UUID NOT NULL REFERENCES consumers(id) ON DELETE CASCADE,
    name VARCHAR(255),
    key_prefix VARCHAR(64) NOT NULL,
    key_hash VARCHAR(128) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_consumer_keys_key_hash ON consumer_keys(key_hash);
CREATE INDEX IF NOT EXISTS idx_consumer_keys_tenant_id ON consumer_keys(tenant_id);
CREATE INDEX IF NOT EXISTS idx_consumer_keys_consumer_id ON consumer_keys(consumer_id);
CREATE INDEX IF NOT EXISTS idx_consumer_keys_tenant_id_id ON consumer_keys(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_consumer_keys_tenant_id_consumer_id ON consumer_keys(tenant_id, consumer_id);
