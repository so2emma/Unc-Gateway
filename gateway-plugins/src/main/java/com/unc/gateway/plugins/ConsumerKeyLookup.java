package com.unc.gateway.plugins;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Data access component that issues hash-based lookups against consumer API keys
 * persisted in PostgreSQL.
 */
@Component
public class ConsumerKeyLookup {

    static final String SELECT_KEY_BY_HASH_SQL = """
            SELECT
                ck.id AS key_id,
                ck.tenant_id AS tenant_id,
                ck.consumer_id AS consumer_id,
                ck.name AS key_name,
                ck.status AS key_status,
                c.username AS username
            FROM consumer_keys ck
            LEFT JOIN consumers c ON ck.consumer_id = c.id
            WHERE ck.key_hash = :keyHash
              AND ck.status = 'ACTIVE'
            """;

    private final DatabaseClient databaseClient;

    public ConsumerKeyLookup(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    /**
     * Resolves a raw API key by computing its SHA-256 hash and looking up the active key record.
     * Never stores, logs, or compares cleartext key material.
     *
     * @param rawKey inbound raw API key
     * @return {@link Mono} emitting the owning {@link ConsumerIdentity}, or empty if missing/invalid/revoked
     */
    public Mono<ConsumerIdentity> lookup(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Mono.empty();
        }
        String keyHash = sha256Hex(rawKey.trim());
        return lookupByHash(keyHash);
    }

    /**
     * Resolves a raw API key within a tenant context. The key always resolves to its own owning
     * tenant rather than the caller's implied tenant context.
     *
     * @param rawKey          inbound raw API key
     * @param contextTenantId optional caller tenant ID context
     * @return {@link Mono} emitting the owning {@link ConsumerIdentity}, or empty if invalid
     */
    public Mono<ConsumerIdentity> lookup(String rawKey, UUID contextTenantId) {
        return lookup(rawKey);
    }

    /**
     * Resolves a consumer identity directly by the stored key hash.
     *
     * @param keyHash SHA-256 hex string of the API key
     * @return {@link Mono} emitting the owning {@link ConsumerIdentity}, or empty if invalid
     */
    public Mono<ConsumerIdentity> lookupByHash(String keyHash) {
        if (keyHash == null || keyHash.isBlank()) {
            return Mono.empty();
        }
        return databaseClient.sql(SELECT_KEY_BY_HASH_SQL)
                .bind("keyHash", keyHash.trim())
                .map(this::mapRow)
                .one();
    }

    ConsumerIdentity mapRow(Row row, RowMetadata metadata) {
        UUID keyId = row.get("key_id", UUID.class);
        UUID tenantId = row.get("tenant_id", UUID.class);
        UUID consumerId = row.get("consumer_id", UUID.class);
        String keyName = row.get("key_name", String.class);
        String username = row.get("username", String.class);

        return new ConsumerIdentity(tenantId, consumerId, keyId, keyName, username);
    }

    /**
     * Computes the SHA-256 hash in lowercase hexadecimal.
     */
    public static String sha256Hex(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }
}
