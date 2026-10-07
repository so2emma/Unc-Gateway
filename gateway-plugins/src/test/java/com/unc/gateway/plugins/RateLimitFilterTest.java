package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private RedisSlidingWindowRateLimiter rateLimiter;
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimiter = Mockito.mock(RedisSlidingWindowRateLimiter.class);
        rateLimitFilter = new RateLimitFilter(rateLimiter);
    }

    @Test
    @DisplayName("RateLimitFilter: quota check allowed passes downstream and sets headers")
    void testAllowedRequestSetsHeadersAndPassesThrough() {
        UUID consumerId = UUID.randomUUID();
        when(rateLimiter.tryAcquire(eq(consumerId.toString()), eq(10L), eq(60L)))
                .thenReturn(Mono.just(RateLimitResult.allowed(9L)));

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(KeyAuthFilter.ATTR_CONSUMER_ID, consumerId);

        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.setName("rate-limit");
        pluginConfig.setConfig(Map.of("limit", 10, "window_seconds", 60));
        exchange.getAttributes().put("plugin_config_rate-limit", pluginConfig);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("9");
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("RateLimitFilter: rejected quota check returns HTTP 429 and Retry-After header")
    void testRejectedRequestReturns429WithRetryAfter() {
        UUID consumerId = UUID.randomUUID();
        long retryAfter = 8L;
        when(rateLimiter.tryAcquire(eq(consumerId.toString()), eq(5L), eq(10L)))
                .thenReturn(Mono.just(RateLimitResult.rejected(retryAfter)));

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(KeyAuthFilter.ATTR_CONSUMER_ID, consumerId);

        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.setName("rate-limit");
        pluginConfig.setConfig(Map.of("limit", 5, "windowSeconds", 10));
        exchange.getAttributes().put("plugin_config_rate-limit", pluginConfig);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("8");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
    }
}
