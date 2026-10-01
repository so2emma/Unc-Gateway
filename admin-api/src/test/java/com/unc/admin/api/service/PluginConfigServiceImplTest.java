package com.unc.admin.api.service;

import com.unc.admin.api.dto.PluginConfigDto;
import com.unc.admin.api.entity.PluginConfigEntity;
import com.unc.admin.api.plugin.BuiltInPluginSchemas;
import com.unc.admin.api.repository.ConsumerRepository;
import com.unc.admin.api.repository.PluginConfigRepository;
import com.unc.admin.api.repository.RouteRepository;
import com.unc.admin.api.repository.ServiceRepository;
import com.unc.admin.api.service.impl.PluginConfigServiceImpl;
import com.unc.admin.api.tenant.TenantContext;
import com.unc.gateway.plugins.api.PluginRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PluginConfigServiceImplTest {

    private static final UUID TENANT_A = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID CONFIG_ID = UUID.fromString("f5eebc99-9c0b-4ef8-bb6d-6bb9bd380f66");
    private static final UUID SERVICE_ID = UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380b22");

    @Mock
    private PluginConfigRepository pluginConfigRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private ConsumerRepository consumerRepository;

    private PluginConfigServiceImpl pluginConfigService;

    @BeforeEach
    void setUp() {
        PluginRegistry registry = new PluginRegistry();
        BuiltInPluginSchemas.registerAll(registry);
        pluginConfigService = new PluginConfigServiceImpl(
                pluginConfigRepository, serviceRepository, routeRepository, consumerRepository, registry);
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("createPluginConfig - a valid key-auth payload is accepted and persisted under the caller's tenant")
    void testCreateValidKeyAuthConfig() {
        given(pluginConfigRepository.save(any(PluginConfigEntity.class))).willAnswer(invocation -> {
            PluginConfigEntity toSave = invocation.getArgument(0);
            toSave.setId(CONFIG_ID);
            return toSave;
        });

        PluginConfigDto dto = new PluginConfigDto();
        dto.setPluginName("key-auth");
        dto.setEnabled(true);
        dto.setConfig(Map.of("key_names", List.of("X-API-Key")));

        PluginConfigDto created = pluginConfigService.createPluginConfig(dto);

        ArgumentCaptor<PluginConfigEntity> captor = ArgumentCaptor.forClass(PluginConfigEntity.class);
        verify(pluginConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_A);
        assertThat(captor.getValue().getName()).isEqualTo("key-auth");
        assertThat(created.getId()).isEqualTo(CONFIG_ID);
        assertThat(created.getPluginName()).isEqualTo("key-auth");
        assertThat(created.getConfig()).containsEntry("key_names", List.of("X-API-Key"));
    }

    @Test
    @DisplayName("createPluginConfig - an empty config for key-auth is accepted")
    void testCreateKeyAuthWithEmptyConfig() {
        given(pluginConfigRepository.save(any(PluginConfigEntity.class))).willAnswer(i -> i.getArgument(0));

        PluginConfigDto dto = new PluginConfigDto();
        dto.setPluginName("key-auth");
        dto.setConfig(Map.of());

        PluginConfigDto created = pluginConfigService.createPluginConfig(dto);

        assertThat(created.getName()).isEqualTo("key-auth");
        assertThat(created.getEnabled()).isTrue();
        assertThat(created.getOrdering()).isZero();
    }

    @Test
    @DisplayName("createPluginConfig - a payload naming a plugin absent from PluginRegistry is rejected")
    void testCreateUnknownPluginRejected() {
        PluginConfigDto dto = new PluginConfigDto();
        dto.setPluginName("totally-not-a-plugin");

        assertThatThrownBy(() -> pluginConfigService.createPluginConfig(dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown plugin: totally-not-a-plugin");

        verify(pluginConfigRepository, never()).save(any(PluginConfigEntity.class));
    }

    @Test
    @DisplayName("createPluginConfig - a payload failing the resolved plugin schema is rejected with a descriptive error")
    void testCreateSchemaViolationRejected() {
        PluginConfigDto dto = new PluginConfigDto();
        dto.setPluginName("rate-limit");
        dto.setConfig(Map.of("limit", -5, "window_seconds", 60));

        assertThatThrownBy(() -> pluginConfigService.createPluginConfig(dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("rate-limit: 'limit' must be a positive integer");

        verify(pluginConfigRepository, never()).save(any(PluginConfigEntity.class));
    }

    @Test
    @DisplayName("createPluginConfig - schema violations are surfaced as 400 Bad Request")
    void testSchemaViolationStatusIsBadRequest() {
        PluginConfigDto dto = new PluginConfigDto();
        dto.setPluginName("jwt-auth");
        dto.setConfig(Map.of("algorithm", "HS256"));

        ResponseStatusException ex = (ResponseStatusException) org.assertj.core.api.Assertions
                .catchThrowable(() -> pluginConfigService.createPluginConfig(dto));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("jwt-auth: either 'secret' or 'public_key' is required");
    }

    @Test
    @DisplayName("createPluginConfig - a missing plugin name is rejected")
    void testCreateMissingPluginNameRejected() {
        PluginConfigDto dto = new PluginConfigDto();

        assertThatThrownBy(() -> pluginConfigService.createPluginConfig(dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("pluginName is required");
    }

    @Test
    @DisplayName("createPluginConfig - attaching to a service owned by another tenant is rejected")
    void testCreateWithForeignServiceScopeRejected() {
        given(serviceRepository.existsByIdAndTenantId(SERVICE_ID, TENANT_A)).willReturn(false);

        PluginConfigDto dto = new PluginConfigDto();
        dto.setPluginName("key-auth");
        dto.setServiceId(SERVICE_ID);

        assertThatThrownBy(() -> pluginConfigService.createPluginConfig(dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Service not found for this tenant");

        verify(pluginConfigRepository, never()).save(any(PluginConfigEntity.class));
    }

    @Test
    @DisplayName("updatePluginConfig - an invalid config for the stored plugin is rejected and nothing is mutated")
    void testUpdateSchemaViolationRejected() {
        PluginConfigEntity existing = new PluginConfigEntity();
        existing.setId(CONFIG_ID);
        existing.setTenantId(TENANT_A);
        existing.setName("logging");
        existing.setConfig(new java.util.LinkedHashMap<>(Map.of("level", "INFO")));

        given(pluginConfigRepository.findByIdAndTenantId(CONFIG_ID, TENANT_A)).willReturn(Optional.of(existing));

        PluginConfigDto dto = new PluginConfigDto();
        dto.setConfig(Map.of("level", "LOUD"));

        assertThatThrownBy(() -> pluginConfigService.updatePluginConfig(CONFIG_ID, dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("logging: unsupported 'level' value 'LOUD'");

        verify(pluginConfigRepository, never()).save(any(PluginConfigEntity.class));
        assertThat(existing.getConfig()).containsEntry("level", "INFO");
    }

    @Test
    @DisplayName("updatePluginConfig - a valid update re-validates and persists the new config")
    void testUpdateValidConfigPersisted() {
        PluginConfigEntity existing = new PluginConfigEntity();
        existing.setId(CONFIG_ID);
        existing.setTenantId(TENANT_A);
        existing.setName("rate-limit");
        existing.setConfig(new java.util.LinkedHashMap<>(Map.of("limit", 10, "window_seconds", 60)));

        given(pluginConfigRepository.findByIdAndTenantId(CONFIG_ID, TENANT_A)).willReturn(Optional.of(existing));
        given(pluginConfigRepository.save(any(PluginConfigEntity.class))).willAnswer(i -> i.getArgument(0));

        PluginConfigDto dto = new PluginConfigDto();
        dto.setConfig(Map.of("limit", 500, "window_seconds", 1));
        dto.setEnabled(false);

        PluginConfigDto updated = pluginConfigService.updatePluginConfig(CONFIG_ID, dto);

        assertThat(updated.getConfig()).containsEntry("limit", 500);
        assertThat(updated.getEnabled()).isFalse();
        assertThat(updated.getName()).isEqualTo("rate-limit");
    }

    @Test
    @DisplayName("updatePluginConfig - a config owned by another tenant is not found and never mutated")
    void testUpdateForeignConfigRejected() {
        given(pluginConfigRepository.findByIdAndTenantId(CONFIG_ID, TENANT_A)).willReturn(Optional.empty());

        PluginConfigDto dto = new PluginConfigDto();
        dto.setPluginName("key-auth");

        assertThatThrownBy(() -> pluginConfigService.updatePluginConfig(CONFIG_ID, dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Plugin config not found");

        verify(pluginConfigRepository, never()).save(any(PluginConfigEntity.class));
    }

    @Test
    @DisplayName("deletePluginConfig - deletion is scoped by tenant_id and rejects foreign rows")
    void testDeleteIsTenantScoped() {
        given(pluginConfigRepository.existsByIdAndTenantId(CONFIG_ID, TENANT_A)).willReturn(false);

        assertThatThrownBy(() -> pluginConfigService.deletePluginConfig(CONFIG_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Plugin config not found");

        verify(pluginConfigRepository, never()).deleteByIdAndTenantId(any(), any());
    }
}
