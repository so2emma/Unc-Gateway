package com.unc.admin.api.service;

import com.unc.admin.api.dto.ConsumerDto;
import com.unc.admin.api.entity.ConsumerEntity;
import com.unc.admin.api.repository.ConsumerRepository;
import com.unc.admin.api.service.impl.ConsumerServiceImpl;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConsumerServiceImplTest {

    private static final UUID TENANT_A = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID TENANT_B = UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380b22");
    private static final UUID CONSUMER_ID = UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380c33");

    @Mock
    private ConsumerRepository consumerRepository;

    private ConsumerServiceImpl consumerService;

    @BeforeEach
    void setUp() {
        consumerService = new ConsumerServiceImpl(consumerRepository);
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("createConsumer - persists the consumer under the caller's tenant_id")
    void testCreatePersistsUnderCallerTenant() {
        given(consumerRepository.existsByUsernameAndTenantId("acme-corp", TENANT_A)).willReturn(false);
        given(consumerRepository.save(any(ConsumerEntity.class))).willAnswer(invocation -> {
            ConsumerEntity toSave = invocation.getArgument(0);
            toSave.setId(CONSUMER_ID);
            return toSave;
        });

        ConsumerDto request = new ConsumerDto();
        request.setName("acme-corp");
        request.setEmail("dev@acme.example");
        request.setOrganization("Acme Corp");

        ConsumerDto created = consumerService.createConsumer(request);

        ArgumentCaptor<ConsumerEntity> captor = ArgumentCaptor.forClass(ConsumerEntity.class);
        verify(consumerRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_A);
        assertThat(captor.getValue().getUsername()).isEqualTo("acme-corp");
        assertThat(created.getId()).isEqualTo(CONSUMER_ID);
        assertThat(created.getTenantId()).isEqualTo(TENANT_A);
        assertThat(created.getName()).isEqualTo("acme-corp");
        assertThat(created.getOrganization()).isEqualTo("Acme Corp");
    }

    @Test
    @DisplayName("createConsumer - rejects a request missing the required identifying name")
    void testCreateRejectsMissingName() {
        ConsumerDto request = new ConsumerDto();
        request.setEmail("dev@acme.example");

        assertThatThrownBy(() -> consumerService.createConsumer(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Consumer name is required");

        verify(consumerRepository, never()).save(any(ConsumerEntity.class));
    }

    @Test
    @DisplayName("createConsumer - rejects a malformed email address")
    void testCreateRejectsMalformedEmail() {
        ConsumerDto request = new ConsumerDto();
        request.setName("acme-corp");
        request.setEmail("not-an-email");

        assertThatThrownBy(() -> consumerService.createConsumer(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not a valid email address");

        verify(consumerRepository, never()).save(any(ConsumerEntity.class));
    }

    @Test
    @DisplayName("createConsumer - rejects a username already taken within the same tenant")
    void testCreateRejectsDuplicateUsernameWithinTenant() {
        given(consumerRepository.existsByUsernameAndTenantId("acme-corp", TENANT_A)).willReturn(true);

        ConsumerDto request = new ConsumerDto();
        request.setName("acme-corp");

        assertThatThrownBy(() -> consumerService.createConsumer(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists for this tenant");

        verify(consumerRepository, never()).save(any(ConsumerEntity.class));
    }

    @Test
    @DisplayName("updateConsumer - a consumer owned by another tenant is not found and is never mutated")
    void testUpdateOtherTenantConsumerRejected() {
        given(consumerRepository.findByIdAndTenantId(CONSUMER_ID, TENANT_A)).willReturn(Optional.empty());

        ConsumerDto request = new ConsumerDto();
        request.setName("hijacked");

        assertThatThrownBy(() -> consumerService.updateConsumer(CONSUMER_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Consumer not found");

        verify(consumerRepository, never()).save(any(ConsumerEntity.class));
    }

    @Test
    @DisplayName("updateConsumer - applies changes to a consumer owned by the caller's tenant")
    void testUpdateAppliesChanges() {
        ConsumerEntity existing = new ConsumerEntity();
        existing.setId(CONSUMER_ID);
        existing.setTenantId(TENANT_A);
        existing.setUsername("acme-corp");

        given(consumerRepository.findByIdAndTenantId(CONSUMER_ID, TENANT_A)).willReturn(Optional.of(existing));
        given(consumerRepository.existsByUsernameAndTenantId("acme-renamed", TENANT_A)).willReturn(false);
        given(consumerRepository.save(any(ConsumerEntity.class))).willAnswer(i -> i.getArgument(0));

        ConsumerDto request = new ConsumerDto();
        request.setName("acme-renamed");
        request.setEmail("ops@acme.example");

        ConsumerDto updated = consumerService.updateConsumer(CONSUMER_ID, request);

        assertThat(updated.getUsername()).isEqualTo("acme-renamed");
        assertThat(updated.getEmail()).isEqualTo("ops@acme.example");
        assertThat(updated.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    @DisplayName("deleteConsumer - deletion is always scoped by the caller's tenant_id")
    void testDeleteIsTenantScoped() {
        TenantContext.setTenantId(TENANT_B);
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_ID, TENANT_B)).willReturn(true);

        consumerService.deleteConsumer(CONSUMER_ID);

        verify(consumerRepository).deleteByIdAndTenantId(CONSUMER_ID, TENANT_B);
    }

    @Test
    @DisplayName("deleteConsumer - a consumer owned by another tenant is never deleted")
    void testDeleteOtherTenantConsumerRejected() {
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_ID, TENANT_A)).willReturn(false);

        assertThatThrownBy(() -> consumerService.deleteConsumer(CONSUMER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Consumer not found");

        verify(consumerRepository, never()).deleteByIdAndTenantId(any(), any());
    }
}
