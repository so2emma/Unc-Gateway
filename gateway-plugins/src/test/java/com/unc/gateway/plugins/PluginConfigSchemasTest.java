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
}
