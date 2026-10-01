package com.unc.admin.api.service;

import com.unc.admin.api.dto.ConsumerKeyDto;
import com.unc.admin.api.dto.IssueConsumerKeyRequest;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped API key issuance, listing, and revocation for a consumer. Every operation first
 * verifies the target consumer belongs to the authenticated caller's tenant.
 */
public interface ConsumerKeyService {

    /**
     * Issues a new API key for the consumer. The returned DTO is the only place the raw key material
     * is ever exposed; only its hash is persisted.
     */
    ConsumerKeyDto issueKey(UUID consumerId, IssueConsumerKeyRequest request);

    List<ConsumerKeyDto> listKeys(UUID consumerId);

    /** Revokes the key, marking it {@code REVOKED}. The key metadata is retained for audit. */
    void revokeKey(UUID consumerId, UUID keyId);
}
