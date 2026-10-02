package com.unc.admin.api.plugin;

import com.unc.gateway.plugins.api.ConfigValidator;
import com.unc.gateway.plugins.api.GatewayFilter;
import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginRegistry;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers the built-in plugin names and their configuration schemas into a {@link PluginRegistry}.
 * <p>
 * {@code admin-api} uses the registry purely as the authoritative source of "which plugins exist and
 * what does a valid config for them look like" when validating {@code /api/admin/plugin-configs}
 * payloads. It never executes a filter chain, so each plugin is registered with a pass-through
 * placeholder filter — the runtime filter implementations live in {@code gateway-plugins} and are
 * loaded by {@code gateway-core}.
 */
public final class BuiltInPluginSchemas {

    public static final String KEY_AUTH = "key-auth";
    public static final String RATE_LIMIT = "rate-limit";
    public static final String JWT_AUTH = "jwt-auth";
    public static final String REQUEST_TRANSFORM = "request-transform";
    public static final String LOGGING = "logging";

    private static final Set<String> SUPPORTED_JWT_ALGORITHMS =
            Set.of("HS256", "HS384", "HS512", "RS256", "RS384", "RS512");

    private static final Set<String> SUPPORTED_LOG_LEVELS =
            Set.of("DEBUG", "INFO", "WARN", "ERROR");

    private BuiltInPluginSchemas() {
    }

    /**
     * Registers every built-in plugin name and schema validator into the supplied registry.
     *
     * @param registry the registry to populate
     */
    public static void registerAll(PluginRegistry registry) {
        registry.register(KEY_AUTH, passThrough(KEY_AUTH), BuiltInPluginSchemas::validateKeyAuth);
        registry.register(RATE_LIMIT, passThrough(RATE_LIMIT), BuiltInPluginSchemas::validateRateLimit);
        registry.register(JWT_AUTH, passThrough(JWT_AUTH), BuiltInPluginSchemas::validateJwtAuth);
        registry.register(REQUEST_TRANSFORM, passThrough(REQUEST_TRANSFORM), BuiltInPluginSchemas::validateRequestTransform);
        registry.register(LOGGING, passThrough(LOGGING), BuiltInPluginSchemas::validateLogging);
    }

    private static GatewayFilter passThrough(String pluginName) {
        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                return chain.filter(exchange);
            }

            @Override
            public String getName() {
                return pluginName;
            }
        };
    }

    // --- Schemas -----------------------------------------------------------------------------

    /** {@code key-auth}: optional {@code key_names} (non-empty strings) and {@code hide_credentials}. */
    static void validateKeyAuth(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return;
        }
        rejectUnknownKeys(KEY_AUTH, config, Set.of("key_names", "hide_credentials"));
        if (config.containsKey("key_names")) {
            List<String> keyNames = requireStringList(KEY_AUTH, config, "key_names");
            if (keyNames.isEmpty()) {
                throw new IllegalArgumentException("key-auth: 'key_names' must contain at least one header name");
            }
        }
        if (config.containsKey("hide_credentials")) {
            requireBoolean(KEY_AUTH, config, "hide_credentials");
        }
    }

    /** {@code rate-limit}: requires positive integers {@code limit} and {@code window_seconds}. */
    static void validateRateLimit(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException(
                    "rate-limit: 'limit' and 'window_seconds' are required positive integers");
        }
        rejectUnknownKeys(RATE_LIMIT, config, Set.of("limit", "window_seconds", "policy"));
        requirePositiveInt(RATE_LIMIT, config, "limit");
        requirePositiveInt(RATE_LIMIT, config, "window_seconds");
    }

    /** {@code jwt-auth}: requires {@code secret} or {@code public_key}; optional {@code algorithm}. */
    static void validateJwtAuth(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("jwt-auth: either 'secret' or 'public_key' is required");
        }
        rejectUnknownKeys(JWT_AUTH, config, Set.of("secret", "public_key", "algorithm", "claims"));

        boolean hasSecret = isNonBlankString(config.get("secret"));
        boolean hasPublicKey = isNonBlankString(config.get("public_key"));
        if (!hasSecret && !hasPublicKey) {
            throw new IllegalArgumentException("jwt-auth: either 'secret' or 'public_key' is required");
        }
        if (config.containsKey("algorithm")) {
            String algorithm = requireString(JWT_AUTH, config, "algorithm");
            if (!SUPPORTED_JWT_ALGORITHMS.contains(algorithm.toUpperCase())) {
                throw new IllegalArgumentException("jwt-auth: unsupported 'algorithm' value '" + algorithm
                        + "', expected one of " + SUPPORTED_JWT_ALGORITHMS);
            }
        }
        if (config.containsKey("claims")) {
            requireStringList(JWT_AUTH, config, "claims");
        }
    }

    /** {@code request-transform}: optional {@code add_headers} map and {@code remove_headers} list. */
    static void validateRequestTransform(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return;
        }
        rejectUnknownKeys(REQUEST_TRANSFORM, config, Set.of("add_headers", "remove_headers"));
        if (config.containsKey("add_headers")) {
            Object raw = config.get("add_headers");
            if (!(raw instanceof Map<?, ?> headers)) {
                throw new IllegalArgumentException("request-transform: 'add_headers' must be an object of header name to value");
            }
            for (Map.Entry<?, ?> entry : headers.entrySet()) {
                if (!(entry.getKey() instanceof String) || !isNonBlankString(entry.getKey())) {
                    throw new IllegalArgumentException("request-transform: 'add_headers' keys must be non-empty strings");
                }
                if (!(entry.getValue() instanceof String)) {
                    throw new IllegalArgumentException("request-transform: 'add_headers' value for '"
                            + entry.getKey() + "' must be a string");
                }
            }
        }
        if (config.containsKey("remove_headers")) {
            requireStringList(REQUEST_TRANSFORM, config, "remove_headers");
        }
    }

    /** {@code logging}: optional {@code level} restricted to the supported log levels. */
    static void validateLogging(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return;
        }
        rejectUnknownKeys(LOGGING, config, Set.of("level", "include_body"));
        if (config.containsKey("level")) {
            String level = requireString(LOGGING, config, "level");
            if (!SUPPORTED_LOG_LEVELS.contains(level.toUpperCase())) {
                throw new IllegalArgumentException("logging: unsupported 'level' value '" + level
                        + "', expected one of " + SUPPORTED_LOG_LEVELS);
            }
        }
        if (config.containsKey("include_body")) {
            requireBoolean(LOGGING, config, "include_body");
        }
    }

    // --- Shared primitive checks --------------------------------------------------------------

    private static void rejectUnknownKeys(String plugin, Map<String, Object> config, Set<String> allowed) {
        for (String key : config.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(plugin + ": unknown config field '" + key
                        + "', expected one of " + allowed);
            }
        }
    }

    private static boolean isNonBlankString(Object value) {
        return value instanceof String s && !s.trim().isEmpty();
    }

    private static String requireString(String plugin, Map<String, Object> config, String field) {
        Object value = config.get(field);
        if (!isNonBlankString(value)) {
            throw new IllegalArgumentException(plugin + ": '" + field + "' must be a non-empty string");
        }
        return (String) value;
    }

    private static void requireBoolean(String plugin, Map<String, Object> config, String field) {
        if (!(config.get(field) instanceof Boolean)) {
            throw new IllegalArgumentException(plugin + ": '" + field + "' must be a boolean");
        }
    }

    private static void requirePositiveInt(String plugin, Map<String, Object> config, String field) {
        Object value = config.get(field);
        if (!(value instanceof Number number) || value instanceof Double || value instanceof Float) {
            throw new IllegalArgumentException(plugin + ": '" + field + "' must be a positive integer");
        }
        if (number.longValue() <= 0) {
            throw new IllegalArgumentException(plugin + ": '" + field + "' must be a positive integer");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> requireStringList(String plugin, Map<String, Object> config, String field) {
        Object value = config.get(field);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(plugin + ": '" + field + "' must be an array of strings");
        }
        for (Object element : list) {
            if (!isNonBlankString(element)) {
                throw new IllegalArgumentException(plugin + ": '" + field + "' must be an array of non-empty strings");
            }
        }
        return (List<String>) list;
    }

    /** Exposes the built-in validators for direct unit testing. */
    public static ConfigValidator validatorFor(String pluginName) {
        return switch (pluginName) {
            case KEY_AUTH -> BuiltInPluginSchemas::validateKeyAuth;
            case RATE_LIMIT -> BuiltInPluginSchemas::validateRateLimit;
            case JWT_AUTH -> BuiltInPluginSchemas::validateJwtAuth;
            case REQUEST_TRANSFORM -> BuiltInPluginSchemas::validateRequestTransform;
            case LOGGING -> BuiltInPluginSchemas::validateLogging;
            default -> throw new IllegalArgumentException("Unknown plugin: " + pluginName);
        };
    }
}
