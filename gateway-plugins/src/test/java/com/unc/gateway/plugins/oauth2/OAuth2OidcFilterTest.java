package com.unc.gateway.plugins.oauth2;

import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuth2OidcFilterTest {

    @Test
    @DisplayName("OAuth2OidcFilter: valid JWKS-mode JWT with sufficient scope results in HTTP pass-through with claims attached")
    void testValidJwksModeTokenPassesThroughWithClaims() {
        JwksJwtValidator jwtValidator = mock(JwksJwtValidator.class);
        TokenIntrospectionClient introspectionClient = mock(TokenIntrospectionClient.class);
        ScopeEnforcer scopeEnforcer = new ScopeEnforcer();
        OAuth2OidcFilter filter = new OAuth2OidcFilter(jwtValidator, introspectionClient, scopeEnforcer);

        String token = "valid-jwks-jwt-token";
        Map<String, Object> config = Map.of(
                "mode", "jwks",
                "jwks_uri", "https://keycloak.local/certs",
                "required_scopes", List.of("api:read")
        );

        Map<String, Object> claims = Map.of(
                "sub", "user-42",
                "tenant_id", "tenant-alpha",
                "scope", "api:read api:write"
        );
        when(jwtValidator.validate(eq(token), any())).thenReturn(Mono.just(claims));

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/data")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.setName("oauth2-oidc");
        pluginConfig.setConfig(config);
        exchange.getAttributes().put("plugin_config_oauth2-oidc", pluginConfig);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamCalled.set(true);

            // Claims assertions
            assertThat(mutatedExchange.<Map<String, Object>>getAttribute(OAuth2OidcFilter.ATTR_OAUTH2_CLAIMS)).isNotNull();
            assertThat(mutatedExchange.<String>getAttribute(OAuth2OidcFilter.ATTR_OAUTH2_SUBJECT)).isEqualTo("user-42");
            assertThat(mutatedExchange.<String>getAttribute("consumer_id")).isEqualTo("user-42");
            assertThat(mutatedExchange.<String>getAttribute("tenant_id")).isEqualTo("tenant-alpha");
            assertThat(mutatedExchange.<Set<String>>getAttribute(OAuth2OidcFilter.ATTR_OAUTH2_SCOPES))
                    .containsExactlyInAnyOrder("api:read", "api:write");

            // Downstream headers
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-Consumer-Id")).isEqualTo("user-42");
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-OAuth-Scope")).contains("api:read");

            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("OAuth2OidcFilter: missing Authorization header results in HTTP 401")
    void testMissingAuthorizationHeaderReturns401() {
        JwksJwtValidator jwtValidator = mock(JwksJwtValidator.class);
        TokenIntrospectionClient introspectionClient = mock(TokenIntrospectionClient.class);
        OAuth2OidcFilter filter = new OAuth2OidcFilter(jwtValidator, introspectionClient, new ScopeEnforcer());

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/data").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("OAuth2OidcFilter: insufficient scopes result in HTTP 403 with WWW-Authenticate: Bearer error=\"insufficient_scope\"")
    void testInsufficientScopesReturns403WithWwwAuthenticate() {
        JwksJwtValidator jwtValidator = mock(JwksJwtValidator.class);
        TokenIntrospectionClient introspectionClient = mock(TokenIntrospectionClient.class);
        ScopeEnforcer scopeEnforcer = new ScopeEnforcer();
        OAuth2OidcFilter filter = new OAuth2OidcFilter(jwtValidator, introspectionClient, scopeEnforcer);

        String token = "token-without-admin-scope";
        Map<String, Object> config = Map.of(
                "mode", "jwks",
                "jwks_uri", "https://keycloak.local/certs",
                "required_scopes", List.of("api:admin")
        );

        Map<String, Object> claims = Map.of(
                "sub", "user-regular",
                "scope", "api:read"
        );
        when(jwtValidator.validate(eq(token), any())).thenReturn(Mono.just(claims));

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.setName("oauth2-oidc");
        pluginConfig.setConfig(config);
        exchange.getAttributes().put("plugin_config_oauth2-oidc", pluginConfig);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer error=\"insufficient_scope\"");
    }

    @Test
    @DisplayName("OAuth2OidcFilter: introspection mode delegates to TokenIntrospectionClient and accepts active token")
    void testIntrospectionModeValidTokenPasses() {
        JwksJwtValidator jwtValidator = mock(JwksJwtValidator.class);
        TokenIntrospectionClient introspectionClient = mock(TokenIntrospectionClient.class);
        ScopeEnforcer scopeEnforcer = new ScopeEnforcer();
        OAuth2OidcFilter filter = new OAuth2OidcFilter(jwtValidator, introspectionClient, scopeEnforcer);

        String token = "opaque-token-xyz";
        Map<String, Object> config = Map.of(
                "mode", "introspect",
                "introspection_endpoint", "https://keycloak.local/introspect",
                "client_id", "gateway",
                "client_secret", "secret",
                "required_scopes", List.of("api:read")
        );

        Map<String, Object> claims = Map.of(
                "active", true,
                "sub", "opaque-user",
                "scope", "api:read"
        );
        when(introspectionClient.introspect(eq(token), any())).thenReturn(Mono.just(claims));

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/data")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.setName("oauth2-oidc");
        pluginConfig.setConfig(config);
        exchange.getAttributes().put("plugin_config_oauth2-oidc", pluginConfig);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamCalled.set(true);
            assertThat(mutatedExchange.<String>getAttribute(OAuth2OidcFilter.ATTR_OAUTH2_SUBJECT)).isEqualTo("opaque-user");
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isTrue();
    }

    @Test
    @DisplayName("OAuth2OidcFilter: introspection mode rejects inactive token with HTTP 401")
    void testIntrospectionModeInactiveTokenReturns401() {
        JwksJwtValidator jwtValidator = mock(JwksJwtValidator.class);
        TokenIntrospectionClient introspectionClient = mock(TokenIntrospectionClient.class);
        OAuth2OidcFilter filter = new OAuth2OidcFilter(jwtValidator, introspectionClient, new ScopeEnforcer());

        String token = "inactive-token";
        Map<String, Object> config = Map.of(
                "mode", "introspect",
                "introspection_endpoint", "https://keycloak.local/introspect",
                "client_id", "gateway",
                "client_secret", "secret"
        );

        when(introspectionClient.introspect(eq(token), any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/data")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.setName("oauth2-oidc");
        pluginConfig.setConfig(config);
        exchange.getAttributes().put("plugin_config_oauth2-oidc", pluginConfig);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
