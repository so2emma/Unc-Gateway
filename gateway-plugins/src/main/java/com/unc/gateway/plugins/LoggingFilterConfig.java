package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.ConfigValidator;

import java.util.*;

/**
 * Configuration schema and payload validator for the {@code logging} plugin.
 */
public class LoggingFilterConfig implements ConfigValidator {

    public static final String PLUGIN_NAME = "logging";
    public static final String DEFAULT_LEVEL = "INFO";

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "level",
            "log_level",
            "logLevel",
            "include_headers",
            "includeHeaders",
            "include_request_headers",
            "includeRequestHeaders",
            "include_response_headers",
            "includeResponseHeaders",
            "include_body",
            "includeBody"
    );

    private static final Set<String> SUPPORTED_LOG_LEVELS = Set.of(
            "DEBUG",
            "INFO",
            "WARN",
            "ERROR",
            "TRACE"
    );

    private final String level;
    private final boolean includeHeaders;
    private final boolean includeRequestHeaders;
    private final boolean includeResponseHeaders;
    private final boolean includeBody;

    public LoggingFilterConfig() {
        this(DEFAULT_LEVEL, false, false, false, false);
    }

    public LoggingFilterConfig(
            String level,
            boolean includeHeaders,
            boolean includeRequestHeaders,
            boolean includeResponseHeaders,
            boolean includeBody
    ) {
        this.level = level != null ? level.toUpperCase() : DEFAULT_LEVEL;
        this.includeHeaders = includeHeaders;
        this.includeRequestHeaders = includeRequestHeaders;
        this.includeResponseHeaders = includeResponseHeaders;
        this.includeBody = includeBody;
    }

    public static LoggingFilterConfig fromConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return new LoggingFilterConfig();
        }
        return new LoggingFilterConfig(
                extractLevel(config),
                shouldIncludeHeaders(config),
                shouldIncludeRequestHeaders(config),
                shouldIncludeResponseHeaders(config),
                shouldIncludeBody(config)
        );
    }

    @Override
    public void validate(Map<String, Object> config) throws IllegalArgumentException {
        if (config == null || config.isEmpty()) {
            return;
        }

        for (String key : config.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("logging: unknown config field '" + key
                        + "', expected one of " + ALLOWED_KEYS);
            }
        }

        String lvl = null;
        if (config.containsKey("level")) {
            lvl = requireString(config, "level");
        } else if (config.containsKey("log_level")) {
            lvl = requireString(config, "log_level");
        } else if (config.containsKey("logLevel")) {
            lvl = requireString(config, "logLevel");
        }

        if (lvl != null && !SUPPORTED_LOG_LEVELS.contains(lvl.toUpperCase())) {
            throw new IllegalArgumentException("logging: unsupported 'level' value '" + lvl
                    + "', expected one of [DEBUG, INFO, WARN, ERROR]");
        }

        for (String boolKey : List.of(
                "include_headers", "includeHeaders",
                "include_request_headers", "includeRequestHeaders",
                "include_response_headers", "includeResponseHeaders",
                "include_body", "includeBody"
        )) {
            if (config.containsKey(boolKey)) {
                requireBoolean(config, boolKey);
            }
        }
    }

    private static String requireString(Map<String, Object> config, String field) {
        Object value = config.get(field);
        if (!(value instanceof String s) || s.trim().isEmpty()) {
            throw new IllegalArgumentException("logging: '" + field + "' must be a non-empty string");
        }
        return s.trim();
    }

    private static void requireBoolean(Map<String, Object> config, String field) {
        if (!(config.get(field) instanceof Boolean)) {
            throw new IllegalArgumentException("logging: '" + field + "' must be a boolean");
        }
    }

    public static String extractLevel(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return DEFAULT_LEVEL;
        }
        Object val = config.get("level");
        if (val == null) {
            val = config.get("log_level");
        }
        if (val == null) {
            val = config.get("logLevel");
        }
        if (val instanceof String s && !s.isBlank()) {
            return s.trim().toUpperCase();
        }
        return DEFAULT_LEVEL;
    }

    public static boolean shouldIncludeHeaders(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return false;
        }
        Object val = config.get("include_headers");
        if (val == null) {
            val = config.get("includeHeaders");
        }
        return Boolean.TRUE.equals(val);
    }

    public static boolean shouldIncludeRequestHeaders(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return false;
        }
        Object val = config.get("include_request_headers");
        if (val == null) {
            val = config.get("includeRequestHeaders");
        }
        return Boolean.TRUE.equals(val);
    }

    public static boolean shouldIncludeResponseHeaders(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return false;
        }
        Object val = config.get("include_response_headers");
        if (val == null) {
            val = config.get("includeResponseHeaders");
        }
        return Boolean.TRUE.equals(val);
    }

    public static boolean shouldIncludeBody(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return false;
        }
        Object val = config.get("include_body");
        if (val == null) {
            val = config.get("includeBody");
        }
        return Boolean.TRUE.equals(val);
    }

    public String getLevel() {
        return level;
    }

    public boolean isIncludeHeaders() {
        return includeHeaders;
    }

    public boolean isIncludeRequestHeaders() {
        return includeRequestHeaders;
    }

    public boolean isIncludeResponseHeaders() {
        return includeResponseHeaders;
    }

    public boolean isIncludeBody() {
        return includeBody;
    }
}
