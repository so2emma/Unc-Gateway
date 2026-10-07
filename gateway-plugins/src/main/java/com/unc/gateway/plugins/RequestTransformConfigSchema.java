package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.ConfigValidator;

import java.util.*;

/**
 * Configuration schema and validator for the {@code request-transform} plugin.
 */
public class RequestTransformConfigSchema implements ConfigValidator {

    public static final String PLUGIN_NAME = "request-transform";

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "add_headers",
            "addHeaders",
            "remove_headers",
            "removeHeaders",
            "rename_headers",
            "renameHeaders"
    );

    @Override
    public void validate(Map<String, Object> config) throws IllegalArgumentException {
        if (config == null || config.isEmpty()) {
            return;
        }

        for (String key : config.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("request-transform: unknown config field '" + key
                        + "', expected one of " + ALLOWED_KEYS);
            }
        }

        if (config.containsKey("add_headers")) {
            validateHeaderMap(config.get("add_headers"), "add_headers");
        }
        if (config.containsKey("addHeaders")) {
            validateHeaderMap(config.get("addHeaders"), "addHeaders");
        }

        if (config.containsKey("remove_headers")) {
            validateHeaderList(config.get("remove_headers"), "remove_headers");
        }
        if (config.containsKey("removeHeaders")) {
            validateHeaderList(config.get("removeHeaders"), "removeHeaders");
        }

        if (config.containsKey("rename_headers")) {
            validateHeaderMap(config.get("rename_headers"), "rename_headers");
        }
        if (config.containsKey("renameHeaders")) {
            validateHeaderMap(config.get("renameHeaders"), "renameHeaders");
        }
    }

    private void validateHeaderMap(Object value, String fieldName) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("request-transform: '" + fieldName + "' must be an object of header name to value");
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String k) || k.trim().isEmpty()) {
                throw new IllegalArgumentException("request-transform: '" + fieldName + "' keys must be non-empty strings");
            }
            if (!(entry.getValue() instanceof String v) || v.trim().isEmpty()) {
                throw new IllegalArgumentException("request-transform: '" + fieldName + "' value for '"
                        + entry.getKey() + "' must be a non-empty string");
            }
        }
    }

    private void validateHeaderList(Object value, String fieldName) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("request-transform: '" + fieldName + "' must be an array of strings");
        }
        for (Object item : list) {
            if (!(item instanceof String s) || s.trim().isEmpty()) {
                throw new IllegalArgumentException("request-transform: '" + fieldName + "' must be an array of non-empty strings");
            }
        }
    }

    public static Map<String, String> extractAddHeaders(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Collections.emptyMap();
        }
        Object raw = config.get("add_headers");
        if (raw == null) {
            raw = config.get("addHeaders");
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(entry.getKey().toString().trim(), entry.getValue().toString().trim());
                }
            }
            return Collections.unmodifiableMap(result);
        }
        return Collections.emptyMap();
    }

    public static List<String> extractRemoveHeaders(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Collections.emptyList();
        }
        Object raw = config.get("remove_headers");
        if (raw == null) {
            raw = config.get("removeHeaders");
        }
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s && !s.isBlank()) {
                    result.add(s.trim());
                }
            }
            return Collections.unmodifiableList(result);
        }
        return Collections.emptyList();
    }

    public static Map<String, String> extractRenameHeaders(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Collections.emptyMap();
        }
        Object raw = config.get("rename_headers");
        if (raw == null) {
            raw = config.get("renameHeaders");
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(entry.getKey().toString().trim(), entry.getValue().toString().trim());
                }
            }
            return Collections.unmodifiableMap(result);
        }
        return Collections.emptyMap();
    }
}
