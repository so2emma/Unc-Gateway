-- V9__add_service_tls_flags.sql: Add TLS and mTLS configuration flags to services table
ALTER TABLE services
    ADD COLUMN IF NOT EXISTS tls_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS mtls_enabled BOOLEAN NOT NULL DEFAULT FALSE;
