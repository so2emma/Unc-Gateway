package com.unc.gateway.plugins;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PluginConfigSchemasTest {

    private final KeyAuthConfigSchema keyAuthSchema = new KeyAuthConfigSchema();
    private final RateLimitConfigSchema rateLimitSchema = new RateLimitConfigSchema();

    @Test
    @DisplayName("KeyAuthConfigSchema: accepts empty config and default headers")
    void testKeyAuthValidEmpty() {
        assertThatCode(() -> keyAuthSchema.validate(Map.of())).doesNotThrowAnyException();
        assertThat(KeyAuthConfigSchema.extractKeyNames(Map.of()))
                .containsExactly("X-Api-Key", "apikey", "X-API-KEY");
        assertThat(KeyAuthConfigSchema.shouldHideCredentials(Map.of())).isFalse();
    }

    @Test
    @DisplayName("KeyAuthConfigSchema: accepts custom key_names and hide_credentials")
    void testKeyAuthValidCustom() {
        Map<String, Object> config = Map.of(
                "key_names", List.of("custom-key-hdr"),
                "hide_credentials", true
        );
        assertThatCode(() -> keyAuthSchema.validate(config)).doesNotThrowAnyException();
        assertThat(KeyAuthConfigSchema.extractKeyNames(config)).containsExactly("custom-key-hdr");
        assertThat(KeyAuthConfigSchema.shouldHideCredentials(config)).isTrue();
    }

    @Test
    @DisplayName("KeyAuthConfigSchema: rejects invalid fields and empty key_names list")
    void testKeyAuthInvalid() {
        assertThatThrownBy(() -> keyAuthSchema.validate(Map.of("key_names", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain at least one header name");

        assertThatThrownBy(() -> keyAuthSchema.validate(Map.of("unrecognized", "field")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown config field 'unrecognized'");

        assertThatThrownBy(() -> keyAuthSchema.validate(Map.of("hide_credentials", "not-boolean")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a boolean");
    }

    @Test
    @DisplayName("RateLimitConfigSchema: accepts valid limit and window_seconds / windowSeconds")
    void testRateLimitValid() {
        Map<String, Object> config1 = Map.of("limit", 100, "window_seconds", 60);
        assertThatCode(() -> rateLimitSchema.validate(config1)).doesNotThrowAnyException();
        assertThat(RateLimitConfigSchema.extractLimit(config1)).isEqualTo(100L);
        assertThat(RateLimitConfigSchema.extractWindowSeconds(config1)).isEqualTo(60L);

        Map<String, Object> config2 = Map.of("limit", 5, "windowSeconds", 10);
        assertThatCode(() -> rateLimitSchema.validate(config2)).doesNotThrowAnyException();
        assertThat(RateLimitConfigSchema.extractLimit(config2)).isEqualTo(5L);
        assertThat(RateLimitConfigSchema.extractWindowSeconds(config2)).isEqualTo(10L);
    }

    @Test
    @DisplayName("RateLimitConfigSchema: rejects missing or non-positive values")
    void testRateLimitInvalid() {
        assertThatThrownBy(() -> rateLimitSchema.validate(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> rateLimitSchema.validate(Map.of("limit", -1, "window_seconds", 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'limit' must be a positive integer");

        assertThatThrownBy(() -> rateLimitSchema.validate(Map.of("limit", 5, "window_seconds", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'window_seconds' must be a positive integer");

        assertThatThrownBy(() -> rateLimitSchema.validate(Map.of("limit", 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'window_seconds' must be a positive integer");

        assertThatThrownBy(() -> rateLimitSchema.validate(Map.of("limit", 5, "windowSeconds", 10, "unknown", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown config field 'unknown'");
    }

    private final JwtAuthConfigSchema jwtAuthSchema = new JwtAuthConfigSchema();
    private final RequestTransformConfigSchema requestTransformSchema = new RequestTransformConfigSchema();

    @Test
    @DisplayName("JwtAuthConfigSchema: accepts valid secret, algorithm, and headerName")
    void testJwtAuthValid() {
        Map<String, Object> config1 = Map.of(
                "secret", "my-secret-key",
                "algorithm", "HS256",
                "headerName", "Authorization"
        );
        assertThatCode(() -> jwtAuthSchema.validate(config1)).doesNotThrowAnyException();
        assertThat(JwtAuthConfigSchema.extractSecret(config1)).isEqualTo("my-secret-key");
        assertThat(JwtAuthConfigSchema.extractAlgorithm(config1)).isEqualTo("HS256");
        assertThat(JwtAuthConfigSchema.extractHeaderName(config1)).isEqualTo("Authorization");

        Map<String, Object> config2 = Map.of(
                "public_key", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...",
                "algorithm", "RS256",
                "header_name", "X-Custom-JWT"
        );
        assertThatCode(() -> jwtAuthSchema.validate(config2)).doesNotThrowAnyException();
        assertThat(JwtAuthConfigSchema.extractPublicKey(config2)).isEqualTo("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...");
        assertThat(JwtAuthConfigSchema.extractAlgorithm(config2)).isEqualTo("RS256");
        assertThat(JwtAuthConfigSchema.extractHeaderName(config2)).isEqualTo("X-Custom-JWT");
    }

    @Test
    @DisplayName("JwtAuthConfigSchema: rejects missing secret/public_key or invalid fields")
    void testJwtAuthInvalid() {
        assertThatThrownBy(() -> jwtAuthSchema.validate(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either 'secret' or 'public_key' is required");

        assertThatThrownBy(() -> jwtAuthSchema.validate(Map.of("secret", "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either 'secret' or 'public_key' is required");

        assertThatThrownBy(() -> jwtAuthSchema.validate(Map.of("secret", "key", "algorithm", "MD5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported 'algorithm' value 'MD5'");

        assertThatThrownBy(() -> jwtAuthSchema.validate(Map.of("secret", "key", "invalidField", "val")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown config field 'invalidField'");
    }

    @Test
    @DisplayName("RequestTransformConfigSchema: accepts valid add, remove, and rename configurations")
    void testRequestTransformValid() {
        assertThatCode(() -> requestTransformSchema.validate(Map.of())).doesNotThrowAnyException();

        Map<String, Object> config = Map.of(
                "addHeaders", Map.of("X-Trace-Id", "12345"),
                "removeHeaders", List.of("X-Internal"),
                "renameHeaders", Map.of("X-Old", "X-New")
        );
        assertThatCode(() -> requestTransformSchema.validate(config)).doesNotThrowAnyException();
        assertThat(RequestTransformConfigSchema.extractAddHeaders(config)).containsEntry("X-Trace-Id", "12345");
        assertThat(RequestTransformConfigSchema.extractRemoveHeaders(config)).containsExactly("X-Internal");
        assertThat(RequestTransformConfigSchema.extractRenameHeaders(config)).containsEntry("X-Old", "X-New");

        Map<String, Object> snakeConfig = Map.of(
                "add_headers", Map.of("X-Trace", "abc"),
                "remove_headers", List.of("X-Old-Header"),
                "rename_headers", Map.of("From", "To")
        );
        assertThatCode(() -> requestTransformSchema.validate(snakeConfig)).doesNotThrowAnyException();
        assertThat(RequestTransformConfigSchema.extractAddHeaders(snakeConfig)).containsEntry("X-Trace", "abc");
        assertThat(RequestTransformConfigSchema.extractRemoveHeaders(snakeConfig)).containsExactly("X-Old-Header");
        assertThat(RequestTransformConfigSchema.extractRenameHeaders(snakeConfig)).containsEntry("From", "To");
    }

    @Test
    @DisplayName("RequestTransformConfigSchema: rejects malformed structures or unknown keys")
    void testRequestTransformInvalid() {
        assertThatThrownBy(() -> requestTransformSchema.validate(Map.of("unknownKey", 123)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown config field 'unknownKey'");

        assertThatThrownBy(() -> requestTransformSchema.validate(Map.of("addHeaders", "not-a-map")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'addHeaders' must be an object");

        assertThatThrownBy(() -> requestTransformSchema.validate(Map.of("removeHeaders", "not-a-list")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'removeHeaders' must be an array");

        assertThatThrownBy(() -> requestTransformSchema.validate(Map.of("renameHeaders", List.of("bad"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'renameHeaders' must be an object");
    }

    private final LoggingFilterConfig loggingFilterConfig = new LoggingFilterConfig();
    private final LoggingConfigSchema loggingConfigSchema = new LoggingConfigSchema();

    @Test
    @DisplayName("LoggingFilterConfig: accepts empty config and default values")
    void testLoggingValidEmpty() {
        assertThatCode(() -> loggingFilterConfig.validate(Map.of())).doesNotThrowAnyException();
        assertThatCode(() -> loggingConfigSchema.validate(Map.of())).doesNotThrowAnyException();
        assertThat(LoggingFilterConfig.extractLevel(Map.of())).isEqualTo("INFO");
        assertThat(LoggingFilterConfig.shouldIncludeHeaders(Map.of())).isFalse();
        assertThat(LoggingFilterConfig.shouldIncludeRequestHeaders(Map.of())).isFalse();
        assertThat(LoggingFilterConfig.shouldIncludeResponseHeaders(Map.of())).isFalse();
        assertThat(LoggingFilterConfig.shouldIncludeBody(Map.of())).isFalse();

        LoggingFilterConfig typed = LoggingFilterConfig.fromConfig(Map.of());
        assertThat(typed.getLevel()).isEqualTo("INFO");
        assertThat(typed.isIncludeHeaders()).isFalse();
        assertThat(typed.isIncludeRequestHeaders()).isFalse();
        assertThat(typed.isIncludeResponseHeaders()).isFalse();
        assertThat(typed.isIncludeBody()).isFalse();
    }

    @Test
    @DisplayName("LoggingFilterConfig: accepts valid levels and boolean flags in snake_case and camelCase")
    void testLoggingValidCustom() {
        Map<String, Object> config1 = Map.of(
                "level", "DEBUG",
                "include_headers", true,
                "include_body", false
        );
        assertThatCode(() -> loggingFilterConfig.validate(config1)).doesNotThrowAnyException();
        assertThat(LoggingFilterConfig.extractLevel(config1)).isEqualTo("DEBUG");
        assertThat(LoggingFilterConfig.shouldIncludeHeaders(config1)).isTrue();
        assertThat(LoggingFilterConfig.shouldIncludeBody(config1)).isFalse();

        Map<String, Object> config2 = Map.of(
                "log_level", "WARN",
                "includeHeaders", true,
                "includeRequestHeaders", true,
                "includeResponseHeaders", false
        );
        assertThatCode(() -> loggingFilterConfig.validate(config2)).doesNotThrowAnyException();
        assertThat(LoggingFilterConfig.extractLevel(config2)).isEqualTo("WARN");
        assertThat(LoggingFilterConfig.shouldIncludeHeaders(config2)).isTrue();
        assertThat(LoggingFilterConfig.shouldIncludeRequestHeaders(config2)).isTrue();
        assertThat(LoggingFilterConfig.shouldIncludeResponseHeaders(config2)).isFalse();

        Map<String, Object> config3 = Map.of("logLevel", "ERROR");
        assertThatCode(() -> loggingFilterConfig.validate(config3)).doesNotThrowAnyException();
        assertThat(LoggingFilterConfig.extractLevel(config3)).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("LoggingFilterConfig: rejects unsupported levels, non-boolean flags, or unknown fields")
    void testLoggingInvalid() {
        assertThatThrownBy(() -> loggingFilterConfig.validate(Map.of("level", "VERBOSE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported 'level' value 'VERBOSE'");

        assertThatThrownBy(() -> loggingFilterConfig.validate(Map.of("level", "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'level' must be a non-empty string");

        assertThatThrownBy(() -> loggingFilterConfig.validate(Map.of("include_headers", "true")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'include_headers' must be a boolean");

        assertThatThrownBy(() -> loggingFilterConfig.validate(Map.of("unknown_key", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown config field 'unknown_key'");
    }
}
