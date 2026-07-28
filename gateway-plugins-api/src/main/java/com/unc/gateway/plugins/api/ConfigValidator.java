package com.unc.gateway.plugins.api;

import java.util.Map;

/**
 * Functional interface for validating a plugin's custom configuration map.
 */
@FunctionalInterface
public interface ConfigValidator {
    /**
     * Validates the plugin configuration payload.
     *
     * @param config the configuration payload map
     * @throws IllegalArgumentException if validation fails
     */
    void validate(Map<String, Object> config) throws IllegalArgumentException;
}
