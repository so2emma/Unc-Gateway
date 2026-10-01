package com.unc.admin.api.integration;

import com.unc.admin.api.dto.ConsumerDto;
import com.unc.admin.api.dto.ConsumerKeyDto;
import com.unc.admin.api.dto.IssueConsumerKeyRequest;
import com.unc.admin.api.dto.PluginConfigDto;
import com.unc.admin.api.entity.TenantEntity;
import com.unc.admin.api.repository.ConsumerKeyRepository;
import com.unc.admin.api.repository.ConsumerRepository;
import com.unc.admin.api.repository.PluginConfigRepository;
import com.unc.admin.api.repository.TenantRepository;
import com.unc.admin.api.service.ConsumerKeyService;
import com.unc.admin.api.service.ConsumerService;
import com.unc.admin.api.service.PluginConfigService;
import com.unc.admin.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration coverage of Phase 8 CRUD against a live relational database with real repositories:
 * two tenants each own consumers, consumer keys, and plugin configs, and no tenant-scoped operation
 * may read or mutate another tenant's rows.
 * <p>
 * Runs against H2 in PostgreSQL compatibility mode with the Flyway migrations applied, matching the
 * project's existing migration-test setup. {@link ConsumerCrudPostgresIsolationTest} runs the same
 * assertions against a real {@code postgres:16-alpine} container.
 */
@SpringBootTest
class ConsumerCrudTenantIsolationTest {

    @Autowired
    private ConsumerService consumerService;

    @Autowired
    private ConsumerKeyService consumerKeyService;

    @Autowired
    private PluginConfigService pluginConfigService;

    @Autowired
    private ConsumerRepository consumerRepository;

    @Autowired
    private ConsumerKeyRepository consumerKeyRepository;

    @Autowired
    private PluginConfigRepository pluginConfigRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void seedTenants() {
        consumerKeyRepository.deleteAll();
        pluginConfigRepository.deleteAll();
        consumerRepository.deleteAll();
        tenantRepository.deleteAll();

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantA, "tenant-a", "key-a-" + tenantA, "a@example.com"));
        tenantRepository.save(new TenantEntity(tenantB, "tenant-b", "key-b-" + tenantB, "b@example.com"));
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Consumer CRUD scoped to tenant A never returns or mutates tenant B's consumers")
    void testConsumerCrudIsolation() {
        ConsumerDto consumerA = asTenant(tenantA, () -> createConsumer("shared-name", "a@acme.example"));
        ConsumerDto consumerB = asTenant(tenantB, () -> createConsumer("shared-name", "b@acme.example"));

        // The same username is legitimately owned by both tenants.
        assertThat(consumerA.getId()).isNotEqualTo(consumerB.getId());

        // LIST
        List<ConsumerDto> tenantAConsumers = asTenant(tenantA, () -> consumerService.listConsumers());
        assertThat(tenantAConsumers).hasSize(1);
        assertThat(tenantAConsumers).allSatisfy(c -> assertThat(c.getTenantId()).isEqualTo(tenantA));
        assertThat(tenantAConsumers.get(0).getId()).isEqualTo(consumerA.getId());

        // GET across tenants
        assertThatThrownBy(() -> asTenant(tenantA, () -> consumerService.getConsumer(consumerB.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Consumer not found");

        // UPDATE across tenants must not mutate
        assertThatThrownBy(() -> asTenant(tenantA, () -> {
            ConsumerDto patch = new ConsumerDto();
            patch.setName("hijacked");
            return consumerService.updateConsumer(consumerB.getId(), patch);
        })).isInstanceOf(ResponseStatusException.class);
        assertThat(consumerRepository.findByIdAndTenantId(consumerB.getId(), tenantB))
                .get()
                .satisfies(entity -> assertThat(entity.getUsername()).isEqualTo("shared-name"));

        // DELETE across tenants must not delete
        assertThatThrownBy(() -> asTenant(tenantA, () -> {
            consumerService.deleteConsumer(consumerB.getId());
            return null;
        })).isInstanceOf(ResponseStatusException.class);
        assertThat(consumerRepository.existsByIdAndTenantId(consumerB.getId(), tenantB)).isTrue();
    }

    @Test
    @DisplayName("Consumer key issuance, listing, and revocation are confined to the owning tenant")
    void testConsumerKeyIsolation() {
        ConsumerDto consumerA = asTenant(tenantA, () -> createConsumer("acme-a", "a@acme.example"));
        ConsumerDto consumerB = asTenant(tenantB, () -> createConsumer("acme-b", "b@acme.example"));

        ConsumerKeyDto keyA = asTenant(tenantA,
                () -> consumerKeyService.issueKey(consumerA.getId(), named("portal")));
        ConsumerKeyDto keyB = asTenant(tenantB,
                () -> consumerKeyService.issueKey(consumerB.getId(), named("portal")));

        // The raw key is returned once and is not what gets persisted.
        assertThat(keyA.getKey()).isNotBlank();
        assertThat(consumerKeyRepository.findByIdAndTenantId(keyA.getId(), tenantA))
                .get()
                .satisfies(entity -> assertThat(entity.getKeyHash()).isNotEqualTo(keyA.getKey()));

        // LIST is scoped to the tenant's own consumer.
        List<ConsumerKeyDto> tenantAKeys = asTenant(tenantA, () -> consumerKeyService.listKeys(consumerA.getId()));
        assertThat(tenantAKeys).hasSize(1);
        assertThat(tenantAKeys.get(0).getId()).isEqualTo(keyA.getId());
        assertThat(tenantAKeys.get(0).getKey()).isNull();

        // Listing another tenant's consumer keys is rejected outright.
        assertThatThrownBy(() -> asTenant(tenantA, () -> consumerKeyService.listKeys(consumerB.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Consumer not found");

        // Revoking another tenant's key is rejected and leaves the key ACTIVE.
        assertThatThrownBy(() -> asTenant(tenantA, () -> {
            consumerKeyService.revokeKey(consumerB.getId(), keyB.getId());
            return null;
        })).isInstanceOf(ResponseStatusException.class);
        assertThat(consumerKeyRepository.findByIdAndTenantId(keyB.getId(), tenantB))
                .get()
                .satisfies(entity -> assertThat(entity.getStatus()).isEqualTo("ACTIVE"));

        // Revoking the tenant's own key succeeds.
        asTenant(tenantA, () -> {
            consumerKeyService.revokeKey(consumerA.getId(), keyA.getId());
            return null;
        });
        assertThat(consumerKeyRepository.findByIdAndTenantId(keyA.getId(), tenantA))
                .get()
                .satisfies(entity -> {
                    assertThat(entity.getStatus()).isEqualTo("REVOKED");
                    assertThat(entity.getRevokedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("Plugin config CRUD scoped to tenant A never returns or mutates tenant B's configs")
    void testPluginConfigIsolation() {
        PluginConfigDto configA = asTenant(tenantA, () -> createPluginConfig("key-auth", Map.of()));
        PluginConfigDto configB = asTenant(tenantB,
                () -> createPluginConfig("rate-limit", Map.of("limit", 10, "window_seconds", 60)));

        // The JSON config payload round-trips through the database.
        List<PluginConfigDto> tenantBConfigs = asTenant(tenantB, () -> pluginConfigService.listPluginConfigs());
        assertThat(tenantBConfigs).hasSize(1);
        assertThat(tenantBConfigs.get(0).getConfig()).containsEntry("window_seconds", 60);

        List<PluginConfigDto> tenantAConfigs = asTenant(tenantA, () -> pluginConfigService.listPluginConfigs());
        assertThat(tenantAConfigs).hasSize(1);
        assertThat(tenantAConfigs.get(0).getId()).isEqualTo(configA.getId());
        assertThat(tenantAConfigs).allSatisfy(c -> assertThat(c.getTenantId()).isEqualTo(tenantA));

        assertThatThrownBy(() -> asTenant(tenantA, () -> pluginConfigService.getPluginConfig(configB.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Plugin config not found");

        assertThatThrownBy(() -> asTenant(tenantA, () -> {
            PluginConfigDto patch = new PluginConfigDto();
            patch.setEnabled(false);
            return pluginConfigService.updatePluginConfig(configB.getId(), patch);
        })).isInstanceOf(ResponseStatusException.class);
        assertThat(pluginConfigRepository.findByIdAndTenantId(configB.getId(), tenantB))
                .get()
                .satisfies(entity -> assertThat(entity.getEnabled()).isTrue());

        assertThatThrownBy(() -> asTenant(tenantA, () -> {
            pluginConfigService.deletePluginConfig(configB.getId());
            return null;
        })).isInstanceOf(ResponseStatusException.class);
        assertThat(pluginConfigRepository.existsByIdAndTenantId(configB.getId(), tenantB)).isTrue();
    }

    @Test
    @DisplayName("A plugin config may not be attached to another tenant's consumer")
    void testPluginConfigCannotReferenceForeignConsumer() {
        ConsumerDto consumerB = asTenant(tenantB, () -> createConsumer("acme-b", "b@acme.example"));

        assertThatThrownBy(() -> asTenant(tenantA, () -> {
            PluginConfigDto dto = new PluginConfigDto();
            dto.setPluginName("key-auth");
            dto.setConsumerId(consumerB.getId());
            return pluginConfigService.createPluginConfig(dto);
        }))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Consumer not found for this tenant");

        assertThat(pluginConfigRepository.findByTenantId(tenantA)).isEmpty();
    }

    // --- helpers -------------------------------------------------------------------------------

    private ConsumerDto createConsumer(String name, String email) {
        ConsumerDto dto = new ConsumerDto();
        dto.setName(name);
        dto.setEmail(email);
        dto.setOrganization("Org " + name);
        return consumerService.createConsumer(dto);
    }

    private PluginConfigDto createPluginConfig(String pluginName, Map<String, Object> config) {
        PluginConfigDto dto = new PluginConfigDto();
        dto.setPluginName(pluginName);
        dto.setConfig(config);
        return pluginConfigService.createPluginConfig(dto);
    }

    private IssueConsumerKeyRequest named(String name) {
        IssueConsumerKeyRequest request = new IssueConsumerKeyRequest();
        request.setName(name);
        return request;
    }

    private <T> T asTenant(UUID tenantId, java.util.function.Supplier<T> action) {
        TenantContext.setTenantId(tenantId);
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }
}
