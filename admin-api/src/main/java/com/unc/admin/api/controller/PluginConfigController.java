package com.unc.admin.api.controller;

import com.unc.admin.api.dto.PluginConfigDto;
import com.unc.admin.api.service.PluginConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/plugin-configs")
public class PluginConfigController {

    private final PluginConfigService pluginConfigService;

    public PluginConfigController(PluginConfigService pluginConfigService) {
        this.pluginConfigService = pluginConfigService;
    }

    @PostMapping
    public ResponseEntity<PluginConfigDto> createPluginConfig(@RequestBody PluginConfigDto dto) {
        PluginConfigDto created = pluginConfigService.createPluginConfig(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<PluginConfigDto> listPluginConfigs() {
        return pluginConfigService.listPluginConfigs();
    }

    @GetMapping("/{id}")
    public PluginConfigDto getPluginConfig(@PathVariable("id") UUID id) {
        return pluginConfigService.getPluginConfig(id);
    }

    @PutMapping("/{id}")
    public PluginConfigDto updatePluginConfig(@PathVariable("id") UUID id, @RequestBody PluginConfigDto dto) {
        return pluginConfigService.updatePluginConfig(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePluginConfig(@PathVariable("id") UUID id) {
        pluginConfigService.deletePluginConfig(id);
        return ResponseEntity.noContent().build();
    }
}
