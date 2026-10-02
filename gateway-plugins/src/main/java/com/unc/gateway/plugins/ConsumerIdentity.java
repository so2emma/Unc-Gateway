package com.unc.gateway.plugins;

import java.util.UUID;

/**
 * Immutable record representing the authenticated consumer identity and owning tenant.
 */
public record ConsumerIdentity(
        UUID tenantId,
        UUID consumerId,
        UUID keyId,
        String keyName,
        String username
) {
    public ConsumerIdentity(UUID tenantId, UUID consumerId) {
        this(tenantId, consumerId, null, null, null);
    }
}
