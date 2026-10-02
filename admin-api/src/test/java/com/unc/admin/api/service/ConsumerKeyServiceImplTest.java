package com.unc.admin.api.service;

import com.unc.admin.api.dto.ConsumerKeyDto;
import com.unc.admin.api.dto.IssueConsumerKeyRequest;
import com.unc.admin.api.entity.ConsumerKeyEntity;
import com.unc.admin.api.repository.ConsumerKeyRepository;
import com.unc.admin.api.repository.ConsumerRepository;
import com.unc.admin.api.service.impl.ConsumerKeyServiceImpl;
import com.unc.admin.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConsumerKeyServiceImplTest {

    private static final UUID TENANT_A = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID CONSUMER_A = UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380c33");
    private static final UUID CONSUMER_B = UUID.fromString("d3eebc99-9c0b-4ef8-bb6d-6bb9bd380d44");
    private static final UUID KEY_ID = UUID.fromString("e4eebc99-9c0b-4ef8-bb6d-6bb9bd380e55");

    @Mock
    private ConsumerKeyRepository consumerKeyRepository;

    @Mock
    private ConsumerRepository consumerRepository;

    private ConsumerKeyServiceImpl consumerKeyService;

    @BeforeEach
    void setUp() {
        consumerKeyService = new ConsumerKeyServiceImpl(consumerKeyRepository, consumerRepository);
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("issueKey - returns the raw key exactly once and persists only its hash")
    void testIssueKeyStoresOnlyHash() {
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_A, TENANT_A)).willReturn(true);
        given(consumerKeyRepository.existsByKeyHash(any())).willReturn(false);
        given(consumerKeyRepository.save(any(ConsumerKeyEntity.class))).willAnswer(invocation -> {
            ConsumerKeyEntity toSave = invocation.getArgument(0);
            toSave.setId(KEY_ID);
            return toSave;
        });

        IssueConsumerKeyRequest request = new IssueConsumerKeyRequest();
        request.setName("portal-key");

        ConsumerKeyDto issued = consumerKeyService.issueKey(CONSUMER_A, request);

        ArgumentCaptor<ConsumerKeyEntity> captor = ArgumentCaptor.forClass(ConsumerKeyEntity.class);
        verify(consumerKeyRepository).save(captor.capture());
        ConsumerKeyEntity persisted = captor.getValue();

        assertThat(issued.getKey()).isNotBlank().startsWith("unc_key_");
        // The raw key material must never be persisted in any column.
        assertThat(persisted.getKeyHash()).isNotEqualTo(issued.getKey());
        assertThat(persisted.getKeyHash()).isEqualTo(sha256Hex(issued.getKey()));
        assertThat(issued.getKey()).contains(persisted.getKeyPrefix());
        assertThat(persisted.getKeyPrefix()).isNotEqualTo(issued.getKey());
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_A);
        assertThat(persisted.getConsumerId()).isEqualTo(CONSUMER_A);
        assertThat(persisted.getStatus()).isEqualTo(ConsumerKeyEntity.STATUS_ACTIVE);
        assertThat(persisted.getName()).isEqualTo("portal-key");
    }

    @Test
    @DisplayName("issueKey - a subsequent listing never exposes the raw key, only its hash reference")
    void testListKeysNeverExposesRawKey() {
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_A, TENANT_A)).willReturn(true);

        ConsumerKeyEntity stored = new ConsumerKeyEntity();
        stored.setId(KEY_ID);
        stored.setTenantId(TENANT_A);
        stored.setConsumerId(CONSUMER_A);
        stored.setKeyPrefix("unc_key_abcd1234");
        stored.setKeyHash("f".repeat(64));
        stored.setStatus(ConsumerKeyEntity.STATUS_ACTIVE);

        given(consumerKeyRepository.findByConsumerIdAndTenantId(CONSUMER_A, TENANT_A)).willReturn(List.of(stored));

        List<ConsumerKeyDto> keys = consumerKeyService.listKeys(CONSUMER_A);

        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).getKey()).isNull();
        assertThat(keys.get(0).getKeyHash()).isEqualTo("f".repeat(64));
        assertThat(keys.get(0).getKeyPrefix()).isEqualTo("unc_key_abcd1234");
    }

    @Test
    @DisplayName("issueKey - generates a distinct key on every issuance")
    void testIssueKeyGeneratesUniqueKeys() {
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_A, TENANT_A)).willReturn(true);
        given(consumerKeyRepository.existsByKeyHash(any())).willReturn(false);
        given(consumerKeyRepository.save(any(ConsumerKeyEntity.class))).willAnswer(i -> i.getArgument(0));

        Set<String> issuedKeys = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            issuedKeys.add(consumerKeyService.issueKey(CONSUMER_A, null).getKey());
        }

        assertThat(issuedKeys).hasSize(25);
    }

    @Test
    @DisplayName("issueKey - issuing against a consumer owned by another tenant is rejected")
    void testIssueKeyForForeignConsumerRejected() {
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_B, TENANT_A)).willReturn(false);

        assertThatThrownBy(() -> consumerKeyService.issueKey(CONSUMER_B, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Consumer not found");

        verify(consumerKeyRepository, never()).save(any(ConsumerKeyEntity.class));
    }

    @Test
    @DisplayName("revokeKey - marks the key REVOKED and stamps revoked_at")
    void testRevokeKeyMarksRevoked() {
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_A, TENANT_A)).willReturn(true);

        ConsumerKeyEntity stored = new ConsumerKeyEntity();
        stored.setId(KEY_ID);
        stored.setTenantId(TENANT_A);
        stored.setConsumerId(CONSUMER_A);
        stored.setKeyPrefix("unc_key_abcd1234");
        stored.setKeyHash("a".repeat(64));
        stored.setStatus(ConsumerKeyEntity.STATUS_ACTIVE);

        given(consumerKeyRepository.findByIdAndConsumerIdAndTenantId(KEY_ID, CONSUMER_A, TENANT_A))
                .willReturn(Optional.of(stored));
        given(consumerKeyRepository.save(any(ConsumerKeyEntity.class))).willAnswer(i -> i.getArgument(0));

        consumerKeyService.revokeKey(CONSUMER_A, KEY_ID);

        assertThat(stored.getStatus()).isEqualTo(ConsumerKeyEntity.STATUS_REVOKED);
        assertThat(stored.getRevokedAt()).isNotNull();
        verify(consumerKeyRepository).save(stored);
    }

    @Test
    @DisplayName("revokeKey - revoking a key of another tenant's consumer is rejected, not silently succeeded")
    void testRevokeKeyOfForeignConsumerRejected() {
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_B, TENANT_A)).willReturn(false);

        assertThatThrownBy(() -> consumerKeyService.revokeKey(CONSUMER_B, KEY_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Consumer not found");

        verify(consumerKeyRepository, never()).save(any(ConsumerKeyEntity.class));
        verify(consumerKeyRepository, never()).findByIdAndConsumerIdAndTenantId(any(), any(), any());
    }

    @Test
    @DisplayName("revokeKey - a key id belonging to a different tenant's row is rejected as not found")
    void testRevokeForeignKeyIdRejected() {
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_A, TENANT_A)).willReturn(true);
        given(consumerKeyRepository.findByIdAndConsumerIdAndTenantId(KEY_ID, CONSUMER_A, TENANT_A))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> consumerKeyService.revokeKey(CONSUMER_A, KEY_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("API key not found");

        verify(consumerKeyRepository, never()).save(any(ConsumerKeyEntity.class));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
