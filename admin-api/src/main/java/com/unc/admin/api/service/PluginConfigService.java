package com.unc.admin.api.service;

import com.unc.admin.api.dto.PluginConfigDto;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped plugin configuration CRUD. Create and update payloads are validated against the
 * target plugin's schema resolved from {@code PluginRegistry} before anything is persisted.
 */
public interface PluginConfigService {

    PluginConfigDto createPluginConfig(PluginConfigDto dto);

    List<PluginConfigDto> listPluginConfigs();

    PluginConfigDto getPluginConfig(UUID id);

    PluginConfigDto updatePluginConfig(UUID id, PluginConfigDto dto);

    void deletePluginConfig(UUID id);
}
