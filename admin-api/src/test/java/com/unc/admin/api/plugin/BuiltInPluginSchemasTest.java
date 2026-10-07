package com.unc.admin.api.plugin;

import com.unc.gateway.plugins.api.PluginConfig;
import com.unc.gateway.plugins.api.PluginRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuiltInPluginSchemasTest {

    private final PluginRegistry registry = newRegistry();

    private static PluginRegistry newRegistry() {
        PluginRegistry registry = new PluginRegistry();
        BuiltInPluginSchemas.registerAll(registry);
        return registry;
    }

    @Test
    @DisplayName("registerAll - registers every built-in plugin name")
    void testAllBuiltInPluginsRegistered() {
        assertThat(registry.isRegistered("key-auth")).isTrue();
        assertThat(registry.isRegistered("rate-limit")).isTrue();
        assertThat(registry.isRegistered("jwt-auth")).isTrue();
        assertThat(registry.isRegistered("request-transform")).isTrue();
        assertThat(registry.isRegistered("logging")).isTrue();
        assertThat(registry.isRegistered("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("validateConfig - key-auth accepts an empty config and a well-formed key_names array")
    void testKeyAuthSchema() {
        assertThatCode(() -> registry.validateConfig(config("key-auth", Map.of()))).doesNotThrowAnyException();
        assertThatCode(() -> registry.validateConfig(
                config("key-auth", Map.of("key_names", List.of("X-API-Key"), "hide_credentials", true))))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> registry.validateConfig(config("key-auth", Map.of("key_names", "X-API-Key"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'key_names' must be an array of strings");

        assertThatThrownBy(() -> registry.validateConfig(config("key-auth", Map.of("bogus", true))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown config field 'bogus'");
    }

    @Test
    @DisplayName("validateConfig - rate-limit requires positive limit and window_seconds")
    void testRateLimitSchema() {
        assertThatCode(() -> registry.validateConfig(
                config("rate-limit", Map.of("limit", 100, "window_seconds", 60))))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> registry.validateConfig(config("rate-limit", Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'limit' and 'window_seconds' are required");

        assertThatThrownBy(() -> registry.validateConfig(config("rate-limit", Map.of("limit", 10))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'window_seconds' must be a positive integer");
    }

    @Test
    @DisplayName("validateConfig - jwt-auth requires a secret or public_key and a supported algorithm")
    void testJwtAuthSchema() {
        assertThatCode(() -> registry.validateConfig(
                config("jwt-auth", Map.of("secret", "s3cr3t", "algorithm", "HS256"))))
                .doesNotThrowAnyException();

        assertThatCode(() -> registry.validateConfig(
                config("jwt-auth", Map.of("publicKey", "pub-key-data", "algorithm", "RS256", "headerName", "X-Auth"))))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> registry.validateConfig(config("jwt-auth", Map.of("secret", ""))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either 'secret' or 'public_key' is required");

        assertThatThrownBy(() -> registry.validateConfig(
                config("jwt-auth", Map.of("secret", "s3cr3t", "algorithm", "MD5"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported 'algorithm' value 'MD5'");
    }

    @Test
    @DisplayName("validateConfig - request-transform validates header maps and removal lists")
    void testRequestTransformSchema() {
        assertThatCode(() -> registry.validateConfig(config("request-transform", Map.of(
                "add_headers", Map.of("X-Tenant", "acme"),
                "remove_headers", List.of("X-Internal"),
                "rename_headers", Map.of("Old-Hdr", "New-Hdr")))))
                .doesNotThrowAnyException();

        assertThatCode(() -> registry.validateConfig(config("request-transform", Map.of(
                "addHeaders", Map.of("X-Gateway-Trace", "unc-gateway"),
                "removeHeaders", List.of("X-Debug"),
                "renameHeaders", Map.of("X-Old", "X-New")))))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> registry.validateConfig(
                config("request-transform", Map.of("add_headers", List.of("X-Tenant")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'add_headers' must be an object");
    }

    @Test
    @DisplayName("validateConfig - logging restricts level to the supported log levels")
    void testLoggingSchema() {
        assertThatCode(() -> registry.validateConfig(config("logging", Map.of("level", "INFO"))))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> registry.validateConfig(config("logging", Map.of("level", "LOUD"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported 'level' value 'LOUD'");
    }

    @Test
    @DisplayName("validateConfig - an unregistered plugin name is rejected")
    void testUnknownPluginRejected() {
        assertThatThrownBy(() -> registry.validateConfig(config("mystery-plugin", Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown plugin: mystery-plugin");
    }

    private PluginConfig config(String name, Map<String, Object> config) {
        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.setName(name);
        pluginConfig.setConfig(config);
        return pluginConfig;
    }
}
