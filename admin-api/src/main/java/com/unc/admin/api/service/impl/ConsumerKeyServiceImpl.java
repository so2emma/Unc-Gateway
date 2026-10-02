package com.unc.admin.api.service.impl;

import com.unc.admin.api.dto.ConsumerKeyDto;
import com.unc.admin.api.dto.IssueConsumerKeyRequest;
import com.unc.admin.api.entity.ConsumerKeyEntity;
import com.unc.admin.api.repository.ConsumerKeyRepository;
import com.unc.admin.api.repository.ConsumerRepository;
import com.unc.admin.api.service.ConsumerKeyService;
import com.unc.admin.api.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ConsumerKeyServiceImpl implements ConsumerKeyService {

    static final String KEY_PREFIX_LITERAL = "unc_key_";

    /** Number of random bytes of key material; rendered as hex, so the key body is twice this long. */
    private static final int KEY_MATERIAL_BYTES = 24;

    /** How much of the raw key is retained in cleartext for display purposes. */
    private static final int DISPLAY_PREFIX_LENGTH = KEY_PREFIX_LITERAL.length() + 8;

    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final ConsumerKeyRepository consumerKeyRepository;
    private final ConsumerRepository consumerRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ConsumerKeyServiceImpl(ConsumerKeyRepository consumerKeyRepository,
                                 ConsumerRepository consumerRepository) {
        this.consumerKeyRepository = consumerKeyRepository;
        this.consumerRepository = consumerRepository;
    }

    @Override
    public ConsumerKeyDto issueKey(UUID consumerId, IssueConsumerKeyRequest request) {
        UUID tenantId = requireConsumerOwnedByTenant(consumerId);

        String rawKey = null;
        String keyHash = null;
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = generateRawKey();
            String candidateHash = sha256Hex(candidate);
            if (!consumerKeyRepository.existsByKeyHash(candidateHash)) {
                rawKey = candidate;
                keyHash = candidateHash;
                break;
            }
        }
        if (rawKey == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to generate a unique API key, please retry");
        }

        ConsumerKeyEntity entity = new ConsumerKeyEntity();
        entity.setTenantId(tenantId);
        entity.setConsumerId(consumerId);
        entity.setName(resolveKeyName(request));
        entity.setKeyPrefix(rawKey.substring(0, DISPLAY_PREFIX_LENGTH));
        entity.setKeyHash(keyHash);
        entity.setStatus(ConsumerKeyEntity.STATUS_ACTIVE);

        ConsumerKeyEntity saved = consumerKeyRepository.save(entity);

        ConsumerKeyDto dto = toDto(saved);
        // The raw key is returned exactly once, here. It is never persisted and cannot be recovered.
        dto.setKey(rawKey);
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsumerKeyDto> listKeys(UUID consumerId) {
        UUID tenantId = requireConsumerOwnedByTenant(consumerId);
        return consumerKeyRepository.findByConsumerIdAndTenantId(consumerId, tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public void revokeKey(UUID consumerId, UUID keyId) {
        UUID tenantId = requireConsumerOwnedByTenant(consumerId);
        ConsumerKeyEntity entity = consumerKeyRepository
                .findByIdAndConsumerIdAndTenantId(keyId, consumerId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));

        if (ConsumerKeyEntity.STATUS_REVOKED.equals(entity.getStatus())) {
            return;
        }
        entity.setStatus(ConsumerKeyEntity.STATUS_REVOKED);
        entity.setRevokedAt(OffsetDateTime.now());
        consumerKeyRepository.save(entity);
    }

    /**
     * Resolves the caller's tenant and asserts the target consumer belongs to it. A consumer owned by
     * a different tenant is indistinguishable from a non-existent one.
     */
    private UUID requireConsumerOwnedByTenant(UUID consumerId) {
        UUID tenantId = TenantContext.getTenantId();
        if (consumerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consumer id is required");
        }
        if (!consumerRepository.existsByIdAndTenantId(consumerId, tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumer not found");
        }
        return tenantId;
    }

    private String resolveKeyName(IssueConsumerKeyRequest request) {
        if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
            return "default";
        }
        return request.getName().trim();
    }

    private String generateRawKey() {
        byte[] material = new byte[KEY_MATERIAL_BYTES];
        secureRandom.nextBytes(material);
        return KEY_PREFIX_LITERAL + HexFormat.of().formatHex(material);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    private ConsumerKeyDto toDto(ConsumerKeyEntity entity) {
        ConsumerKeyDto dto = new ConsumerKeyDto();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setConsumerId(entity.getConsumerId());
        dto.setName(entity.getName());
        dto.setKeyPrefix(entity.getKeyPrefix());
        dto.setKeyHash(entity.getKeyHash());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setRevokedAt(entity.getRevokedAt());
        return dto;
    }
}
