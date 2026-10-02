package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.ConfigValidator;

import java.util.*;

/**
 * Configuration schema and validator for the {@code key-auth} plugin.
 */
public class KeyAuthConfigSchema implements ConfigValidator {

    public static final String PLUGIN_NAME = "key-auth";
    public static final List<String> DEFAULT_KEY_NAMES = List.of("X-Api-Key", "apikey", "X-API-KEY");

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "key_names",
            "keyNames",
            "hide_credentials",
            "hideCredentials"
    );

    @Override
    public void validate(Map<String, Object> config) throws IllegalArgumentException {
        if (config == null || config.isEmpty()) {
            return;
        }

        for (String key : config.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("key-auth: unknown config field '" + key
                        + "', expected one of " + ALLOWED_KEYS);
            }
        }

        if (config.containsKey("key_names")) {
            validateKeyNames(config.get("key_names"), "key_names");
        }
        if (config.containsKey("keyNames")) {
            validateKeyNames(config.get("keyNames"), "keyNames");
        }

        if (config.containsKey("hide_credentials") && !(config.get("hide_credentials") instanceof Boolean)) {
            throw new IllegalArgumentException("key-auth: 'hide_credentials' must be a boolean");
        }
        if (config.containsKey("hideCredentials") && !(config.get("hideCredentials") instanceof Boolean)) {
            throw new IllegalArgumentException("key-auth: 'hideCredentials' must be a boolean");
        }
    }

    private void validateKeyNames(Object value, String fieldName) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("key-auth: '" + fieldName + "' must be an array of strings");
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("key-auth: '" + fieldName + "' must contain at least one header name");
        }
        for (Object element : list) {
            if (!(element instanceof String s) || s.trim().isEmpty()) {
                throw new IllegalArgumentException("key-auth: '" + fieldName + "' must be an array of non-empty strings");
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static List<String> extractKeyNames(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return DEFAULT_KEY_NAMES;
        }
        Object names = config.get("key_names");
        if (names == null) {
            names = config.get("keyNames");
        }
        if (names instanceof List<?> list && !list.isEmpty()) {
            List<String> result = new ArrayList<>();
            for (Object obj : list) {
                if (obj instanceof String s && !s.trim().isEmpty()) {
                    result.add(s.trim());
                }
            }
            if (!result.isEmpty()) {
                return Collections.unmodifiableList(result);
            }
        }
        return DEFAULT_KEY_NAMES;
    }

    public static boolean shouldHideCredentials(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return false;
        }
        Object hide = config.get("hide_credentials");
        if (hide == null) {
            hide = config.get("hideCredentials");
        }
        return Boolean.TRUE.equals(hide);
    }
}
