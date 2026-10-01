package com.unc.admin.api.service.impl;

import com.unc.admin.api.dto.PluginConfigDto;
import com.unc.admin.api.entity.PluginConfigEntity;
import com.unc.admin.api.repository.ConsumerRepository;
import com.unc.admin.api.repository.PluginConfigRepository;
import com.unc.admin.api.repository.RouteRepository;
import com.unc.admin.api.repository.ServiceRepository;
import com.unc.admin.api.service.PluginConfigService;
import com.unc.admin.api.tenant.TenantContext;
import com.unc.gateway.plugins.api.PluginConfig;
import com.unc.gateway.plugins.api.PluginRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PluginConfigServiceImpl implements PluginConfigService {

    private final PluginConfigRepository pluginConfigRepository;
    private final ServiceRepository serviceRepository;
    private final RouteRepository routeRepository;
    private final ConsumerRepository consumerRepository;
    private final PluginRegistry pluginRegistry;

    public PluginConfigServiceImpl(PluginConfigRepository pluginConfigRepository,
                                  ServiceRepository serviceRepository,
                                  RouteRepository routeRepository,
                                  ConsumerRepository consumerRepository,
                                  PluginRegistry pluginRegistry) {
        this.pluginConfigRepository = pluginConfigRepository;
        this.serviceRepository = serviceRepository;
        this.routeRepository = routeRepository;
        this.consumerRepository = consumerRepository;
        this.pluginRegistry = pluginRegistry;
    }

    @Override
    public PluginConfigDto createPluginConfig(PluginConfigDto dto) {
        UUID tenantId = TenantContext.getTenantId();

        String pluginName = dto.getName();
        if (pluginName == null || pluginName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pluginName is required");
        }
        pluginName = pluginName.trim();

        Map<String, Object> config = dto.getConfig() != null ? dto.getConfig() : new LinkedHashMap<>();
        validateAgainstRegistry(pluginName, config);
        assertScopeTargetsOwnedByTenant(dto, tenantId);

        PluginConfigEntity entity = new PluginConfigEntity();
        entity.setTenantId(tenantId);
        entity.setName(pluginName);
        entity.setServiceId(dto.getServiceId());
        entity.setRouteId(dto.getRouteId());
        entity.setConsumerId(dto.getConsumerId());
        entity.setOrdering(dto.getOrdering() != null ? dto.getOrdering() : 0);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setConfig(new LinkedHashMap<>(config));

        return toDto(pluginConfigRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PluginConfigDto> listPluginConfigs() {
        UUID tenantId = TenantContext.getTenantId();
        return pluginConfigRepository.findByTenantId(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PluginConfigDto getPluginConfig(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        return pluginConfigRepository.findByIdAndTenantId(id, tenantId)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin config not found"));
    }

    @Override
    public PluginConfigDto updatePluginConfig(UUID id, PluginConfigDto dto) {
        UUID tenantId = TenantContext.getTenantId();
        PluginConfigEntity entity = pluginConfigRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin config not found"));

        String pluginName = dto.getName() != null && !dto.getName().trim().isEmpty()
                ? dto.getName().trim()
                : entity.getName();
        Map<String, Object> config = dto.getConfig() != null ? dto.getConfig() : entity.getConfig();

        // Re-validate the resulting (name, config) pair, not just the delta.
        validateAgainstRegistry(pluginName, config);
        assertScopeTargetsOwnedByTenant(dto, tenantId);

        entity.setName(pluginName);
        entity.setConfig(new LinkedHashMap<>(config != null ? config : Map.of()));
        if (dto.getServiceId() != null) {
            entity.setServiceId(dto.getServiceId());
        }
        if (dto.getRouteId() != null) {
            entity.setRouteId(dto.getRouteId());
        }
        if (dto.getConsumerId() != null) {
            entity.setConsumerId(dto.getConsumerId());
        }
        if (dto.getOrdering() != null) {
            entity.setOrdering(dto.getOrdering());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }

        return toDto(pluginConfigRepository.save(entity));
    }

    @Override
    public void deletePluginConfig(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        if (!pluginConfigRepository.existsByIdAndTenantId(id, tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin config not found");
        }
        pluginConfigRepository.deleteByIdAndTenantId(id, tenantId);
    }

    /**
     * Resolves the plugin's schema from {@link PluginRegistry} and validates the payload against it.
     * Unknown plugin names and schema violations are both surfaced as {@code 400 Bad Request} with the
     * registry's own descriptive message.
     */
    private void validateAgainstRegistry(String pluginName, Map<String, Object> config) {
        PluginConfig candidate = new PluginConfig();
        candidate.setName(pluginName);
        candidate.setConfig(config);
        try {
            pluginRegistry.validateConfig(candidate);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /** A plugin config may only be attached to a service, route, or consumer of the caller's tenant. */
    private void assertScopeTargetsOwnedByTenant(PluginConfigDto dto, UUID tenantId) {
        if (dto.getServiceId() != null && !serviceRepository.existsByIdAndTenantId(dto.getServiceId(), tenantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service not found for this tenant");
        }
        if (dto.getRouteId() != null && routeRepository.findByIdAndTenantId(dto.getRouteId(), tenantId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route not found for this tenant");
        }
        if (dto.getConsumerId() != null && !consumerRepository.existsByIdAndTenantId(dto.getConsumerId(), tenantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consumer not found for this tenant");
        }
    }

    private PluginConfigDto toDto(PluginConfigEntity entity) {
        PluginConfigDto dto = new PluginConfigDto();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setServiceId(entity.getServiceId());
        dto.setRouteId(entity.getRouteId());
        dto.setConsumerId(entity.getConsumerId());
        dto.setName(entity.getName());
        dto.setOrdering(entity.getOrdering());
        dto.setEnabled(entity.getEnabled());
        dto.setConfig(entity.getConfig() != null ? new LinkedHashMap<>(entity.getConfig()) : new LinkedHashMap<>());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
