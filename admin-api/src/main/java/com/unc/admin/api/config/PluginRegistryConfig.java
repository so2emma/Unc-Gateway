package com.unc.admin.api.config;

import com.unc.admin.api.plugin.BuiltInPluginSchemas;
import com.unc.gateway.plugins.api.PluginRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the {@link PluginRegistry} used to validate {@code /api/admin/plugin-configs} payloads,
 * pre-populated with the built-in plugin names and their configuration schemas.
 */
@Configuration
public class PluginRegistryConfig {

    @Bean
    public PluginRegistry pluginRegistry() {
        PluginRegistry registry = new PluginRegistry();
        BuiltInPluginSchemas.registerAll(registry);
        return registry;
    }
}
