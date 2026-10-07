-- V1__base_schema.sql: Initial base schema for Unc Gateway Admin API using native UUID types

CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS services (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    connect_timeout INT DEFAULT 6000,
    read_timeout INT DEFAULT 60000,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS routes (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    paths VARCHAR(1024) NOT NULL,
    methods VARCHAR(255),
    protocols VARCHAR(255),
    strip_path BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS consumers (
    id UUID PRIMARY KEY,
    username VARCHAR(255) UNIQUE,
    custom_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plugin_configs (
    id UUID PRIMARY KEY,
    service_id UUID REFERENCES services(id) ON DELETE CASCADE,
    route_id UUID REFERENCES routes(id) ON DELETE CASCADE,
    consumer_id UUID REFERENCES consumers(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    ordering INT NOT NULL DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS request_logs (
    id UUID PRIMARY KEY,
    service_id UUID,
    route_id UUID,
    consumer_id UUID,
    client_ip VARCHAR(45),
    method VARCHAR(10),
    path TEXT,
    status INT,
    latency_ms BIGINT,
    request_size BIGINT,
    response_size BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_routes_service_id ON routes(service_id);
CREATE INDEX IF NOT EXISTS idx_plugin_configs_service_id ON plugin_configs(service_id);
CREATE INDEX IF NOT EXISTS idx_plugin_configs_route_id ON plugin_configs(route_id);
CREATE INDEX IF NOT EXISTS idx_plugin_configs_consumer_id ON plugin_configs(consumer_id);
CREATE INDEX IF NOT EXISTS idx_request_logs_created_at ON request_logs(created_at);
