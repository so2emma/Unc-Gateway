package com.unc.gateway.plugins.oauth2;

import com.unc.gateway.plugins.api.ConfigValidator;

import java.util.*;

/**
 * Configuration schema and validator for the {@code oauth2-oidc} plugin.
 * <p>
 * Supports both JWKS-based JWT validation ({@code mode: jwks}) and token introspection ({@code mode: introspect}).
 */
public class OAuth2OidcConfigSchema implements ConfigValidator {

    public static final String PLUGIN_NAME = "oauth2-oidc";
    public static final String MODE_JWKS = "jwks";
    public static final String MODE_INTROSPECT = "introspect";
    public static final String DEFAULT_MODE = MODE_JWKS;
    public static final String DEFAULT_HEADER_NAME = "Authorization";
    public static final long DEFAULT_JWKS_REFRESH_INTERVAL_SECONDS = 300L;
    public static final long DEFAULT_INTROSPECTION_CACHE_TTL_SECONDS = 60L;

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "mode",
            "jwks_uri",
            "jwksUri",
            "introspection_endpoint",
            "introspectionEndpoint",
            "client_id",
            "clientId",
            "client_secret",
            "clientSecret",
            "issuer",
            "audience",
            "required_scopes",
            "requiredScopes",
            "jwks_refresh_interval_seconds",
            "jwksRefreshIntervalSeconds",
            "introspection_cache_ttl_seconds",
            "introspectionCacheTtlSeconds",
            "header_name",
            "headerName"
    );

    @Override
    public void validate(Map<String, Object> config) throws IllegalArgumentException {
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("oauth2-oidc: configuration must not be empty");
        }

        for (String key : config.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("oauth2-oidc: unknown config field '" + key
                        + "', expected one of " + ALLOWED_KEYS);
            }
        }

        String mode = DEFAULT_MODE;
        if (config.containsKey("mode")) {
            Object modeVal = config.get("mode");
            if (!(modeVal instanceof String s) || s.trim().isEmpty()) {
                throw new IllegalArgumentException("oauth2-oidc: 'mode' must be a non-empty string");
            }
            mode = s.trim().toLowerCase(Locale.ROOT);
            if (!MODE_JWKS.equals(mode) && !MODE_INTROSPECT.equals(mode)) {
                throw new IllegalArgumentException("oauth2-oidc: unsupported 'mode' value '" + s
                        + "', expected one of [" + MODE_JWKS + ", " + MODE_INTROSPECT + "]");
            }
        }

        if (MODE_JWKS.equals(mode)) {
            boolean hasJwksUri = isNonBlankString(config.get("jwks_uri")) || isNonBlankString(config.get("jwksUri"));
            if (!hasJwksUri) {
                throw new IllegalArgumentException("oauth2-oidc: 'jwks_uri' is required in jwks mode");
            }
        } else {
            boolean hasEndpoint = isNonBlankString(config.get("introspection_endpoint"))
                    || isNonBlankString(config.get("introspectionEndpoint"));
            boolean hasClientId = isNonBlankString(config.get("client_id"))
                    || isNonBlankString(config.get("clientId"));
            boolean hasClientSecret = isNonBlankString(config.get("client_secret"))
                    || isNonBlankString(config.get("clientSecret"));
            if (!hasEndpoint || !hasClientId || !hasClientSecret) {
                throw new IllegalArgumentException("oauth2-oidc: 'introspection_endpoint', 'client_id', and 'client_secret' are required in introspect mode");
            }
        }

        if (config.containsKey("issuer")) {
            validateString(config.get("issuer"), "issuer");
        }

        if (config.containsKey("audience")) {
            Object aud = config.get("audience");
            if (!(aud instanceof String) && !(aud instanceof List<?>)) {
                throw new IllegalArgumentException("oauth2-oidc: 'audience' must be a string or array of strings");
            }
        }

        if (config.containsKey("required_scopes")) {
            validateStringList(config.get("required_scopes"), "required_scopes");
        }
        if (config.containsKey("requiredScopes")) {
            validateStringList(config.get("requiredScopes"), "requiredScopes");
        }

        if (config.containsKey("jwks_refresh_interval_seconds")) {
            validatePositiveNumber(config.get("jwks_refresh_interval_seconds"), "jwks_refresh_interval_seconds");
        }
        if (config.containsKey("jwksRefreshIntervalSeconds")) {
            validatePositiveNumber(config.get("jwksRefreshIntervalSeconds"), "jwksRefreshIntervalSeconds");
        }

        if (config.containsKey("introspection_cache_ttl_seconds")) {
            validatePositiveNumber(config.get("introspection_cache_ttl_seconds"), "introspection_cache_ttl_seconds");
        }
        if (config.containsKey("introspectionCacheTtlSeconds")) {
            validatePositiveNumber(config.get("introspectionCacheTtlSeconds"), "introspectionCacheTtlSeconds");
        }

        if (config.containsKey("header_name")) {
            validateString(config.get("header_name"), "header_name");
        }
        if (config.containsKey("headerName")) {
            validateString(config.get("headerName"), "headerName");
        }
    }

    private static void validateString(Object value, String fieldName) {
        if (!(value instanceof String s) || s.trim().isEmpty()) {
            throw new IllegalArgumentException("oauth2-oidc: '" + fieldName + "' must be a non-empty string");
        }
    }

    private static void validateStringList(Object value, String fieldName) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("oauth2-oidc: '" + fieldName + "' must be an array of strings");
        }
        for (Object item : list) {
            if (!(item instanceof String s) || s.trim().isEmpty()) {
                throw new IllegalArgumentException("oauth2-oidc: '" + fieldName + "' must be an array of non-empty strings");
            }
        }
    }

    private static void validatePositiveNumber(Object value, String fieldName) {
        if (!(value instanceof Number number) || value instanceof Double || value instanceof Float) {
            throw new IllegalArgumentException("oauth2-oidc: '" + fieldName + "' must be a positive integer");
        }
        if (number.longValue() <= 0) {
            throw new IllegalArgumentException("oauth2-oidc: '" + fieldName + "' must be a positive integer");
        }
    }

    private static boolean isNonBlankString(Object value) {
        return value instanceof String s && !s.trim().isEmpty();
    }

    public static String extractMode(Map<String, Object> config) {
        if (config == null) {
            return DEFAULT_MODE;
        }
        Object m = config.get("mode");
        if (m instanceof String s && !s.isBlank()) {
            return s.trim().toLowerCase(Locale.ROOT);
        }
        return DEFAULT_MODE;
    }

    public static String extractJwksUri(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Object u = config.get("jwks_uri");
        if (u == null) {
            u = config.get("jwksUri");
        }
        return u instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    public static String extractIntrospectionEndpoint(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Object ep = config.get("introspection_endpoint");
        if (ep == null) {
            ep = config.get("introspectionEndpoint");
        }
        return ep instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    public static String extractClientId(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Object cid = config.get("client_id");
        if (cid == null) {
            cid = config.get("clientId");
        }
        return cid instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    public static String extractClientSecret(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Object cs = config.get("client_secret");
        if (cs == null) {
            cs = config.get("clientSecret");
        }
        return cs instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    public static String extractIssuer(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Object iss = config.get("issuer");
        return iss instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    public static Object extractAudience(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        return config.get("audience");
    }

    @SuppressWarnings("unchecked")
    public static List<String> extractRequiredScopes(Map<String, Object> config) {
        if (config == null) {
            return Collections.emptyList();
        }
        Object scopes = config.get("required_scopes");
        if (scopes == null) {
            scopes = config.get("requiredScopes");
        }
        if (scopes instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s && !s.isBlank()) {
                    result.add(s.trim());
                }
            }
            return Collections.unmodifiableList(result);
        } else if (scopes instanceof String s && !s.isBlank()) {
            return Arrays.stream(s.trim().split("\\s+"))
                    .filter(part -> !part.isBlank())
                    .toList();
        }
        return Collections.emptyList();
    }

    public static long extractJwksRefreshIntervalSeconds(Map<String, Object> config) {
        if (config == null) {
            return DEFAULT_JWKS_REFRESH_INTERVAL_SECONDS;
        }
        Object interval = config.get("jwks_refresh_interval_seconds");
        if (interval == null) {
            interval = config.get("jwksRefreshIntervalSeconds");
        }
        if (interval instanceof Number n && n.longValue() > 0) {
            return n.longValue();
        }
        return DEFAULT_JWKS_REFRESH_INTERVAL_SECONDS;
    }

    public static long extractIntrospectionCacheTtlSeconds(Map<String, Object> config) {
        if (config == null) {
            return DEFAULT_INTROSPECTION_CACHE_TTL_SECONDS;
        }
        Object ttl = config.get("introspection_cache_ttl_seconds");
        if (ttl == null) {
            ttl = config.get("introspectionCacheTtlSeconds");
        }
        if (ttl instanceof Number n && n.longValue() > 0) {
            return n.longValue();
        }
        return DEFAULT_INTROSPECTION_CACHE_TTL_SECONDS;
    }

    public static String extractHeaderName(Map<String, Object> config) {
        if (config == null) {
            return DEFAULT_HEADER_NAME;
        }
        Object h = config.get("header_name");
        if (h == null) {
            h = config.get("headerName");
        }
        if (h instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return DEFAULT_HEADER_NAME;
    }
}
