package com.unc.gateway.core.plugin;

import com.unc.gateway.plugins.PluginConfigLoader;
import com.unc.gateway.plugins.api.GatewayFilter;
import com.unc.gateway.plugins.api.PluginConfig;
import com.unc.gateway.plugins.api.PluginRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PluginChainHookTest {

    private PluginRegistry pluginRegistry;
    private PluginConfigLoader pluginConfigLoader;
    private PluginChainHook pluginChainHook;

    @BeforeEach
    void setUp() {
        pluginRegistry = new PluginRegistry();
        pluginConfigLoader = Mockito.mock(PluginConfigLoader.class);
        pluginChainHook = new PluginChainHook(pluginRegistry, pluginConfigLoader);
    }

    @Test
    @DisplayName("executeChain: passes through to proxyCall when no plugins are enabled")
    void testPassThroughWhenNoPlugins() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean proxyCalled = new AtomicBoolean(false);

        StepVerifier.create(pluginChainHook.executeChain(exchange, List.of(), () -> {
            proxyCalled.set(true);
            return Mono.just(ResponseEntity.ok("proxied".getBytes()));
        }))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(new String(response.getBody())).isEqualTo("proxied");
                })
                .verifyComplete();

        assertThat(proxyCalled.get()).isTrue();
    }

    @Test
    @DisplayName("executeChain: loads cached plugin configs for tenant when empty list passed")
    void testLoadsCachedConfigsForTenant() {
        UUID tenantId = UUID.randomUUID();
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("X-Tenant-Id", tenantId.toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean filterCalled = new AtomicBoolean(false);
        GatewayFilter dummyFilter = (ex, chain) -> {
            filterCalled.set(true);
            return chain.filter(ex);
        };
        pluginRegistry.register("dummy-filter", dummyFilter);

        PluginConfig config = new PluginConfig("1", tenantId.toString(), "dummy-filter", 1, true, Map.of());
        when(pluginConfigLoader.getCachedConfigs(tenantId)).thenReturn(List.of(config));

        StepVerifier.create(pluginChainHook.executeChain(exchange, List.of(), () ->
                Mono.just(ResponseEntity.ok("ok".getBytes()))
        ))
                .assertNext(res -> assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        assertThat(filterCalled.get()).isTrue();
    }

    @Test
    @DisplayName("executeChain: short-circuits on 401 without invoking proxyCall")
    void testShortCircuitsOn401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter rejectFilter = (ex, chain) -> {
            ex.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return ex.getResponse().setComplete();
        };
        pluginRegistry.register("auth-filter", rejectFilter);

        PluginConfig config = new PluginConfig("1", "t1", "auth-filter", 1, true, Map.of());

        AtomicBoolean proxyCalled = new AtomicBoolean(false);

        StepVerifier.create(pluginChainHook.executeChain(exchange, List.of(config), () -> {
            proxyCalled.set(true);
            return Mono.just(ResponseEntity.ok("never".getBytes()));
        }))
                .assertNext(res -> assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .verifyComplete();

        assertThat(proxyCalled.get()).isFalse();
    }

    @Test
    @DisplayName("executeChain: short-circuits on 429 with Retry-After header without invoking proxyCall")
    void testShortCircuitsOn429WithRetryAfter() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter rateLimitFilter = (ex, chain) -> {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            ex.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "10");
            return ex.getResponse().setComplete();
        };
        pluginRegistry.register("rate-limit-filter", rateLimitFilter);

        PluginConfig config = new PluginConfig("1", "t1", "rate-limit-filter", 1, true, Map.of());

        AtomicBoolean proxyCalled = new AtomicBoolean(false);

        StepVerifier.create(pluginChainHook.executeChain(exchange, List.of(config), () -> {
            proxyCalled.set(true);
            return Mono.just(ResponseEntity.ok("never".getBytes()));
        }))
                .assertNext(res -> {
                    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("10");
                })
                .verifyComplete();

        assertThat(proxyCalled.get()).isFalse();
    }
}
