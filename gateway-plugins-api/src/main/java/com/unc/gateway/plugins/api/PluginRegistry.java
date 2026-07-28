package com.unc.gateway.plugins.api;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central registry for managing plugin registrations, schema validation, and filter chain assembly.
 */
public class PluginRegistry {
    private final Map<String, GatewayFilter> filters = new ConcurrentHashMap<>();
    private final Map<String, ConfigValidator> validators = new ConcurrentHashMap<>();

    /**
     * Registers a gateway filter implementation under the specified plugin name.
     *
     * @param pluginName unique plugin identifier
     * @param filter     filter implementation
     * @throws IllegalArgumentException if the plugin name is already registered
     */
    public void register(String pluginName, GatewayFilter filter) {
        register(pluginName, filter, null);
    }

    /**
     * Registers a gateway filter implementation along with a configuration validator.
     *
     * @param pluginName unique plugin identifier
     * @param filter     filter implementation
     * @param validator  configuration payload validator (optional)
     * @throws IllegalArgumentException if the plugin name is already registered
     */
    public void register(String pluginName, GatewayFilter filter, ConfigValidator validator) {
        Objects.requireNonNull(pluginName, "pluginName must not be null");
        Objects.requireNonNull(filter, "filter must not be null");

        if (filters.containsKey(pluginName)) {
            throw new IllegalArgumentException("Plugin already registered: " + pluginName);
        }
        filters.put(pluginName, filter);
        if (validator != null) {
            validators.put(pluginName, validator);
        }
    }

    /**
     * Registers or updates a configuration validator schema for a plugin.
     *
     * @param pluginName unique plugin identifier
     * @param validator  configuration payload validator
     */
    public void registerSchema(String pluginName, ConfigValidator validator) {
        Objects.requireNonNull(pluginName, "pluginName must not be null");
        Objects.requireNonNull(validator, "validator must not be null");
        validators.put(pluginName, validator);
    }

    /**
     * Retrieves the registered filter by plugin name.
     *
     * @param pluginName unique plugin identifier
     * @return registered {@link GatewayFilter} or {@code null}
     */
    public GatewayFilter getFilter(String pluginName) {
        return filters.get(pluginName);
    }

    /**
     * Checks whether a plugin is registered.
     *
     * @param pluginName unique plugin identifier
     * @return true if registered, false otherwise
     */
    public boolean isRegistered(String pluginName) {
        return filters.containsKey(pluginName);
    }

    /**
     * Validates a {@link PluginConfig} payload against registered plugin schemas.
     *
     * @param pluginConfig the configuration to validate
     * @throws IllegalArgumentException if the plugin is unknown or payload validation fails
     */
    public void validateConfig(PluginConfig pluginConfig) {
        if (pluginConfig == null) {
            throw new IllegalArgumentException("PluginConfig must not be null");
        }
        String name = pluginConfig.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Plugin name must not be empty");
        }
        if (!isRegistered(name)) {
            throw new IllegalArgumentException("Unknown plugin: " + name);
        }
        ConfigValidator validator = validators.get(name);
        if (validator != null) {
            validator.validate(pluginConfig.getConfig());
        }
    }

    /**
     * Resolves an ordered {@link GatewayFilterChain} from a list of plugin configurations.
     * <p>
     * Disabled plugin configurations are skipped. Enabled entries are ordered by {@link PluginConfig#getOrder()} ascending.
     * If an enabled configuration references an unknown plugin name, an {@link IllegalArgumentException} is thrown.
     * </p>
     *
     * @param configs list of plugin configurations
     * @return executable filter chain
     * @throws IllegalArgumentException if an enabled config references an unregistered plugin
     */
    public GatewayFilterChain resolveChain(List<PluginConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return exchange -> Mono.empty();
        }

        List<PluginConfig> activeConfigs = configs.stream()
                .filter(Objects::nonNull)
                .filter(PluginConfig::isEnabled)
                .sorted(Comparator.comparingInt(PluginConfig::getOrder))
                .collect(Collectors.toList());

        if (activeConfigs.isEmpty()) {
            return exchange -> Mono.empty();
        }

        List<GatewayFilter> resolvedFilters = new ArrayList<>();
        for (PluginConfig config : activeConfigs) {
            String name = config.getName();
            GatewayFilter filter = getFilter(name);
            if (filter == null) {
                throw new IllegalArgumentException("Unknown plugin: " + name);
            }
            resolvedFilters.add(filter);
        }

        return buildChain(resolvedFilters, 0);
    }

    private GatewayFilterChain buildChain(List<GatewayFilter> filterList, int index) {
        if (index >= filterList.size()) {
            return exchange -> Mono.empty();
        }
        GatewayFilter currentFilter = filterList.get(index);
        GatewayFilterChain nextChain = buildChain(filterList, index + 1);
        return exchange -> currentFilter.filter(exchange, nextChain);
    }
}
