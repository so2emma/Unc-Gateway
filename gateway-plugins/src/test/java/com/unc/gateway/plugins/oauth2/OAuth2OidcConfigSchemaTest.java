package com.unc.gateway.plugins.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2OidcConfigSchemaTest {

    private final OAuth2OidcConfigSchema schema = new OAuth2OidcConfigSchema();

    @Test
    @DisplayName("OAuth2OidcConfigSchema: accepts valid JWKS mode configuration")
    void testValidJwksConfig() {
        Map<String, Object> config = Map.of(
                "mode", "jwks",
                "jwks_uri", "https://keycloak.local/certs",
                "issuer", "https://keycloak.local",
                "audience", "gateway",
                "required_scopes", List.of("api:read"),
                "jwks_refresh_interval_seconds", 120
        );

        assertThatCode(() -> schema.validate(config)).doesNotThrowAnyException();
        assertThat(OAuth2OidcConfigSchema.extractMode(config)).isEqualTo("jwks");
        assertThat(OAuth2OidcConfigSchema.extractJwksUri(config)).isEqualTo("https://keycloak.local/certs");
        assertThat(OAuth2OidcConfigSchema.extractIssuer(config)).isEqualTo("https://keycloak.local");
        assertThat(OAuth2OidcConfigSchema.extractRequiredScopes(config)).containsExactly("api:read");
        assertThat(OAuth2OidcConfigSchema.extractJwksRefreshIntervalSeconds(config)).isEqualTo(120L);
    }

    @Test
    @DisplayName("OAuth2OidcConfigSchema: accepts valid introspect mode configuration")
    void testValidIntrospectConfig() {
        Map<String, Object> config = Map.of(
                "mode", "introspect",
                "introspection_endpoint", "https://keycloak.local/introspect",
                "client_id", "gateway-client",
                "client_secret", "secret-key",
                "introspection_cache_ttl_seconds", 30
        );

        assertThatCode(() -> schema.validate(config)).doesNotThrowAnyException();
        assertThat(OAuth2OidcConfigSchema.extractMode(config)).isEqualTo("introspect");
        assertThat(OAuth2OidcConfigSchema.extractIntrospectionEndpoint(config)).isEqualTo("https://keycloak.local/introspect");
        assertThat(OAuth2OidcConfigSchema.extractClientId(config)).isEqualTo("gateway-client");
        assertThat(OAuth2OidcConfigSchema.extractClientSecret(config)).isEqualTo("secret-key");
        assertThat(OAuth2OidcConfigSchema.extractIntrospectionCacheTtlSeconds(config)).isEqualTo(30L);
    }

    @Test
    @DisplayName("OAuth2OidcConfigSchema: rejects missing jwks_uri in jwks mode")
    void testMissingJwksUriRejected() {
        Map<String, Object> config = Map.of("mode", "jwks");
        assertThatThrownBy(() -> schema.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'jwks_uri' is required in jwks mode");
    }

    @Test
    @DisplayName("OAuth2OidcConfigSchema: rejects missing client credentials in introspect mode")
    void testMissingIntrospectFieldsRejected() {
        Map<String, Object> config = Map.of(
                "mode", "introspect",
                "introspection_endpoint", "https://as.example.com/introspect"
        );
        assertThatThrownBy(() -> schema.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'introspection_endpoint', 'client_id', and 'client_secret' are required in introspect mode");
    }

    @Test
    @DisplayName("OAuth2OidcConfigSchema: rejects unknown config keys and invalid modes")
    void testInvalidFieldsRejected() {
        assertThatThrownBy(() -> schema.validate(Map.of("mode", "invalid_mode", "jwks_uri", "https://a.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported 'mode' value");

        assertThatThrownBy(() -> schema.validate(Map.of("mode", "jwks", "jwks_uri", "https://a.com", "unknown_key", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown config field 'unknown_key'");
    }
}
