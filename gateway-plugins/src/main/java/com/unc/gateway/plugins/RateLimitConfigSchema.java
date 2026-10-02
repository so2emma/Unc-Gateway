package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.ConfigValidator;

import java.util.Map;
import java.util.Set;

/**
 * Configuration schema and validator for the {@code rate-limit} plugin.
 */
public class RateLimitConfigSchema implements ConfigValidator {

    public static final String PLUGIN_NAME = "rate-limit";

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "limit",
            "window_seconds",
            "windowSeconds",
            "policy"
    );

    @Override
    public void validate(Map<String, Object> config) throws IllegalArgumentException {
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException(
                    "rate-limit: 'limit' and 'window_seconds' are required positive integers");
        }

        for (String key : config.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("rate-limit: unknown config field '" + key
                        + "', expected one of " + ALLOWED_KEYS);
            }
        }

        requirePositiveInt(config, "limit");

        boolean hasWindowSeconds = config.containsKey("window_seconds");
        boolean hasWindowSecondsCamel = config.containsKey("windowSeconds");

        if (!hasWindowSeconds && !hasWindowSecondsCamel) {
            throw new IllegalArgumentException("rate-limit: 'window_seconds' must be a positive integer");
        }

        if (hasWindowSeconds) {
            requirePositiveInt(config, "window_seconds");
        }
        if (hasWindowSecondsCamel) {
            requirePositiveInt(config, "windowSeconds");
        }
    }

    private void requirePositiveInt(Map<String, Object> config, String field) {
        Object value = config.get(field);
        if (!(value instanceof Number number) || value instanceof Double || value instanceof Float) {
            throw new IllegalArgumentException("rate-limit: '" + field + "' must be a positive integer");
        }
        if (number.longValue() <= 0) {
            throw new IllegalArgumentException("rate-limit: '" + field + "' must be a positive integer");
        }
    }

    public static long extractLimit(Map<String, Object> config) {
        if (config == null) {
            throw new IllegalArgumentException("rate-limit config cannot be null");
        }
        Object limitObj = config.get("limit");
        if (limitObj instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("rate-limit: 'limit' is missing or not a number");
    }

    public static long extractWindowSeconds(Map<String, Object> config) {
        if (config == null) {
            throw new IllegalArgumentException("rate-limit config cannot be null");
        }
        Object windowObj = config.get("window_seconds");
        if (windowObj == null) {
            windowObj = config.get("windowSeconds");
        }
        if (windowObj instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("rate-limit: 'window_seconds' / 'windowSeconds' is missing or not a number");
    }
}
