-- V8__add_admin_users_api_keys.sql
-- Phase 23: Admin API Authentication & Role-Based Access Control (RBAC)

CREATE TABLE IF NOT EXISTS admin_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    tenant_scope UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS admin_api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_hash VARCHAR(128) NOT NULL UNIQUE,
    label VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    tenant_scope UUID,
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_users_email ON admin_users(email);
CREATE INDEX IF NOT EXISTS idx_admin_api_keys_key_hash ON admin_api_keys(key_hash);
CREATE INDEX IF NOT EXISTS idx_admin_api_keys_tenant_scope ON admin_api_keys(tenant_scope);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- Bootstrap ADMIN user: admin@unc.local / changeme
INSERT INTO admin_users (id, email, password_hash, role, tenant_scope, enabled)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'admin@unc.local',
    '$2a$10$k3kv7VjM0TDcJl7p6Mql7OvjMBPyf8JOqFFfxHydAW/WLROJBnSBq',
    'ADMIN',
    NULL,
    TRUE
)
ON CONFLICT (email) DO NOTHING;
