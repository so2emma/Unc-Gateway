package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {

    private final Instant now = Instant.parse("2026-10-04T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final String secret = "test-jwt-secret-key-123456";

    private JwtVerifier jwtVerifier;
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        jwtVerifier = new JwtVerifier(clock);
        jwtAuthFilter = new JwtAuthFilter(jwtVerifier, secret, "HS256", "Authorization");
    }

    @Test
    @DisplayName("JwtAuthFilter: valid unexpired bearer token passes through chain with decoded claims attached")
    void testValidTokenPassesThrough() {
        long futureExp = now.getEpochSecond() + 3600;
        Map<String, Object> claims = Map.of(
                "sub", "user-uuid-999",
                "role", "editor",
                "exp", futureExp
        );
        String token = JwtVerifier.createHmacToken(claims, secret, "HS256");

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamCalled.set(true);

            assertThat(mutatedExchange.<Map<String, Object>>getAttribute(JwtAuthFilter.ATTR_JWT_CLAIMS)).isNotNull();
            Map<String, Object> attachedClaims = mutatedExchange.getAttribute(JwtAuthFilter.ATTR_JWT_CLAIMS);
            assertThat(attachedClaims.get("sub")).isEqualTo("user-uuid-999");
            assertThat(attachedClaims.get("role")).isEqualTo("editor");

            assertThat(mutatedExchange.<String>getAttribute(JwtAuthFilter.ATTR_JWT_SUBJECT)).isEqualTo("user-uuid-999");
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-Consumer-Id")).isEqualTo("user-uuid-999");

            return Mono.empty();
        };

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("JwtAuthFilter: missing Authorization header rejected with HTTP 401")
    void testMissingAuthorizationHeaderRejectsWith401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("JwtAuthFilter: non-bearer scheme rejected with HTTP 401")
    void testNonBearerSchemeRejectsWith401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("JwtAuthFilter: empty bearer token rejected with HTTP 401")
    void testEmptyBearerTokenRejectsWith401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("Authorization", "Bearer ")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("JwtAuthFilter: token with invalid signature rejected with HTTP 401")
    void testInvalidSignatureRejectsWith401() {
        long futureExp = now.getEpochSecond() + 3600;
        String invalidToken = JwtVerifier.createHmacToken(Map.of("sub", "user", "exp", futureExp), "other-secret", "HS256");

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("Authorization", "Bearer " + invalidToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("JwtAuthFilter: expired token rejected with HTTP 401")
    void testExpiredTokenRejectsWith401() {
        long pastExp = now.getEpochSecond() - 100;
        String expiredToken = JwtVerifier.createHmacToken(Map.of("sub", "user", "exp", pastExp), secret, "HS256");

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("Authorization", "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("JwtAuthFilter: resolves configuration from exchange attribute and custom header")
    void testCustomHeaderAndConfigFromExchange() {
        String customSecret = "tenant-custom-secret";
        long futureExp = now.getEpochSecond() + 3600;
        String token = JwtVerifier.createHmacToken(Map.of("sub", "tenant-user", "exp", futureExp), customSecret, "HS256");

        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.setName("jwt-auth");
        pluginConfig.setConfig(Map.of(
                "secret", customSecret,
                "headerName", "X-Custom-JWT"
        ));

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("X-Custom-JWT", token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("plugin_config_jwt-auth", pluginConfig);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamCalled.set(true);
            assertThat(mutatedExchange.<String>getAttribute(JwtAuthFilter.ATTR_JWT_SUBJECT)).isEqualTo("tenant-user");
            return Mono.empty();
        };

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isTrue();
    }
}
