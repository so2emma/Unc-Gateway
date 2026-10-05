-- V7__seed_full_stack_verification_data.sql
-- Seed baseline services, routes, demo consumer, API keys, and plugin configurations for Phase 22 full-stack verification

INSERT INTO tenants (id, name, status, api_key, email)
VALUES ('cdc20cb4-ecf6-4fcd-9e38-a29233381928', 'Default Tenant', 'ACTIVE', 'unc_live_sec_0582a9028a3a4c66b8cdb3b00168bfa8', 'ops@unc.dev')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    api_key = EXCLUDED.api_key,
    status = EXCLUDED.status;

INSERT INTO services (id, tenant_id, name, url)
VALUES ('e0fd07ff-c526-410d-88b9-53277c08e483', 'cdc20cb4-ecf6-4fcd-9e38-a29233381928', 'echo-service', 'http://mock-upstream:9090')
ON CONFLICT (id) DO UPDATE SET
    url = EXCLUDED.url,
    name = EXCLUDED.name;

INSERT INTO routes (id, tenant_id, service_id, name, paths, strip_path)
VALUES ('a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', 'cdc20cb4-ecf6-4fcd-9e38-a29233381928', 'e0fd07ff-c526-410d-88b9-53277c08e483', 'echo-route', '/api/v1/echo', TRUE)
ON CONFLICT (id) DO UPDATE SET
    paths = EXCLUDED.paths,
    service_id = EXCLUDED.service_id,
    strip_path = EXCLUDED.strip_path;

INSERT INTO consumers (id, tenant_id, username, email, organization)
VALUES ('c1c2c3c4-c5c6-4c7c-8c9c-0c1c2c3c4c5c', 'cdc20cb4-ecf6-4fcd-9e38-a29233381928', 'demo-consumer', 'demo@unc.dev', 'Unc Demo')
ON CONFLICT (id) DO UPDATE SET
    username = EXCLUDED.username,
    email = EXCLUDED.email;

INSERT INTO consumer_keys (id, tenant_id, consumer_id, name, key_prefix, key_hash, status)
VALUES ('d1d2d3d4-d5d6-4d7d-8d9d-0d1d2d3d4d5d', 'cdc20cb4-ecf6-4fcd-9e38-a29233381928', 'c1c2c3c4-c5c6-4c7c-8c9c-0c1c2c3c4c5c', 'Demo API Key', 'unc_key_demo', 'a480e58d00a618725bce573e51f6be7cef507855ba960e9191809ad289bc266d', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
    key_hash = EXCLUDED.key_hash,
    status = 'ACTIVE';

INSERT INTO plugin_configs (id, tenant_id, name, ordering, enabled, config)
VALUES ('573e91a5-afe8-467c-9ad3-8b75d1acd59d', 'cdc20cb4-ecf6-4fcd-9e38-a29233381928', 'key-auth', 1, TRUE, '{}'::jsonb)
ON CONFLICT (id) DO UPDATE SET
    enabled = TRUE,
    ordering = EXCLUDED.ordering,
    config = EXCLUDED.config;

INSERT INTO plugin_configs (id, tenant_id, name, ordering, enabled, config)
VALUES ('673e91a5-afe8-467c-9ad3-8b75d1acd59e', 'cdc20cb4-ecf6-4fcd-9e38-a29233381928', 'rate-limit', 2, TRUE, '{"limit": 5, "window_seconds": 60}'::jsonb)
ON CONFLICT (id) DO UPDATE SET
    enabled = TRUE,
    ordering = EXCLUDED.ordering,
    config = EXCLUDED.config;
