package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.ConfigValidator;

import java.util.*;

/**
 * Configuration schema and validator for the {@code jwt-auth} plugin.
 */
public class JwtAuthConfigSchema implements ConfigValidator {

    public static final String PLUGIN_NAME = "jwt-auth";
    public static final String DEFAULT_HEADER_NAME = "Authorization";
    public static final String DEFAULT_ALGORITHM = "HS256";

    public static final Set<String> SUPPORTED_ALGORITHMS = Set.of(
            "HS256", "HS384", "HS512", "RS256", "RS384", "RS512"
    );

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "secret",
            "public_key",
            "publicKey",
            "algorithm",
            "header_name",
            "headerName",
            "claims"
    );

    @Override
    public void validate(Map<String, Object> config) throws IllegalArgumentException {
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("jwt-auth: either 'secret' or 'public_key' is required");
        }

        for (String key : config.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("jwt-auth: unknown config field '" + key
                        + "', expected one of " + ALLOWED_KEYS);
            }
        }

        boolean hasSecret = isNonBlankString(config.get("secret"));
        boolean hasPublicKey = isNonBlankString(config.get("public_key")) || isNonBlankString(config.get("publicKey"));

        if (!hasSecret && !hasPublicKey) {
            throw new IllegalArgumentException("jwt-auth: either 'secret' or 'public_key' is required");
        }

        if (config.containsKey("algorithm")) {
            Object alg = config.get("algorithm");
            if (!(alg instanceof String s) || s.trim().isEmpty()) {
                throw new IllegalArgumentException("jwt-auth: 'algorithm' must be a non-empty string");
            }
            if (!SUPPORTED_ALGORITHMS.contains(s.trim().toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("jwt-auth: unsupported 'algorithm' value '" + s
                        + "', expected one of " + SUPPORTED_ALGORITHMS);
            }
        }

        if (config.containsKey("header_name")) {
            validateHeaderName(config.get("header_name"), "header_name");
        }
        if (config.containsKey("headerName")) {
            validateHeaderName(config.get("headerName"), "headerName");
        }

        if (config.containsKey("claims")) {
            Object claimsObj = config.get("claims");
            if (!(claimsObj instanceof List<?> list)) {
                throw new IllegalArgumentException("jwt-auth: 'claims' must be an array of strings");
            }
            for (Object item : list) {
                if (!(item instanceof String str) || str.trim().isEmpty()) {
                    throw new IllegalArgumentException("jwt-auth: 'claims' must be an array of non-empty strings");
                }
            }
        }
    }

    private void validateHeaderName(Object value, String fieldName) {
        if (!(value instanceof String s) || s.trim().isEmpty()) {
            throw new IllegalArgumentException("jwt-auth: '" + fieldName + "' must be a non-empty string");
        }
    }

    private static boolean isNonBlankString(Object value) {
        return value instanceof String s && !s.trim().isEmpty();
    }

    public static String extractSecret(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Object s = config.get("secret");
        return s instanceof String str && !str.isBlank() ? str.trim() : null;
    }

    public static String extractPublicKey(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Object pk = config.get("public_key");
        if (pk == null) {
            pk = config.get("publicKey");
        }
        return pk instanceof String str && !str.isBlank() ? str.trim() : null;
    }

    public static String extractAlgorithm(Map<String, Object> config) {
        if (config == null) {
            return DEFAULT_ALGORITHM;
        }
        Object alg = config.get("algorithm");
        if (alg instanceof String str && !str.isBlank()) {
            return str.trim().toUpperCase(Locale.ROOT);
        }
        return DEFAULT_ALGORITHM;
    }

    public static String extractHeaderName(Map<String, Object> config) {
        if (config == null) {
            return DEFAULT_HEADER_NAME;
        }
        Object h = config.get("header_name");
        if (h == null) {
            h = config.get("headerName");
        }
        if (h instanceof String str && !str.isBlank()) {
            return str.trim();
        }
        return DEFAULT_HEADER_NAME;
    }
}
