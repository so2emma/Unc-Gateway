package com.unc.admin.api.controller;

import com.unc.admin.api.dto.PluginConfigDto;
import com.unc.admin.api.security.TenantScopeGuard;
import com.unc.admin.api.service.PluginConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/plugin-configs")
public class PluginConfigController {

    private final PluginConfigService pluginConfigService;
    private final TenantScopeGuard tenantScopeGuard;

    public PluginConfigController(PluginConfigService pluginConfigService, TenantScopeGuard tenantScopeGuard) {
        this.pluginConfigService = pluginConfigService;
        this.tenantScopeGuard = tenantScopeGuard;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ResponseEntity<PluginConfigDto> createPluginConfig(@RequestBody PluginConfigDto dto) {
        tenantScopeGuard.checkTenantScope();
        PluginConfigDto created = pluginConfigService.createPluginConfig(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PluginConfigDto> listPluginConfigs() {
        tenantScopeGuard.checkTenantScope();
        return pluginConfigService.listPluginConfigs();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PluginConfigDto getPluginConfig(@PathVariable("id") UUID id) {
        tenantScopeGuard.checkTenantScope();
        return pluginConfigService.getPluginConfig(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public PluginConfigDto updatePluginConfig(@PathVariable("id") UUID id, @RequestBody PluginConfigDto dto) {
        tenantScopeGuard.checkTenantScope();
        return pluginConfigService.updatePluginConfig(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ResponseEntity<Void> deletePluginConfig(@PathVariable("id") UUID id) {
        tenantScopeGuard.checkTenantScope();
        pluginConfigService.deletePluginConfig(id);
        return ResponseEntity.noContent().build();
    }
}
