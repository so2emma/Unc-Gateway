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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class KeyAuthFilterTest {

    private ConsumerKeyLookup consumerKeyLookup;
    private KeyAuthFilter keyAuthFilter;

    @BeforeEach
    void setUp() {
        consumerKeyLookup = Mockito.mock(ConsumerKeyLookup.class);
        keyAuthFilter = new KeyAuthFilter(consumerKeyLookup);
    }

    @Test
    @DisplayName("KeyAuthFilter: valid non-revoked API key passes through chain with tenant/consumer attached")
    void testValidApiKeyPassesThrough() {
        UUID tenantId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();
        ConsumerIdentity identity = new ConsumerIdentity(tenantId, consumerId, UUID.randomUUID(), "test-key", "user-1");

        when(consumerKeyLookup.lookup("valid-secret-key")).thenReturn(Mono.just(identity));

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("X-Api-Key", "valid-secret-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamCalled.set(true);
            assertThat(mutatedExchange.<ConsumerIdentity>getAttribute(KeyAuthFilter.ATTR_CONSUMER_IDENTITY)).isEqualTo(identity);
            assertThat(mutatedExchange.<UUID>getAttribute(KeyAuthFilter.ATTR_CONSUMER_ID)).isEqualTo(consumerId);
            assertThat(mutatedExchange.<UUID>getAttribute(KeyAuthFilter.ATTR_TENANT_ID)).isEqualTo(tenantId);
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-Consumer-Id")).isEqualTo(consumerId.toString());
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-Tenant-Id")).isEqualTo(tenantId.toString());
            return Mono.empty();
        };

        StepVerifier.create(keyAuthFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("KeyAuthFilter: missing API key returns 401 and never calls downstream chain")
    void testMissingApiKeyRejectsWith401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(keyAuthFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("KeyAuthFilter: unknown or revoked API key returns 401 and never calls downstream chain")
    void testUnknownOrRevokedKeyRejectsWith401() {
        when(consumerKeyLookup.lookup(anyString())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("apikey", "revoked-or-invalid-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(keyAuthFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("KeyAuthFilter: hide_credentials removes API key header from forwarded request")
    void testHideCredentialsRemovesHeader() {
        UUID tenantId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();
        ConsumerIdentity identity = new ConsumerIdentity(tenantId, consumerId);

        when(consumerKeyLookup.lookup("my-secret-key")).thenReturn(Mono.just(identity));

        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.setName("key-auth");
        pluginConfig.setConfig(Map.of("hide_credentials", true));

        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("X-Api-Key", "my-secret-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("plugin_config_key-auth", pluginConfig);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamCalled.set(true);
            assertThat(mutatedExchange.getRequest().getHeaders().containsKey("X-Api-Key")).isFalse();
            return Mono.empty();
        };

        StepVerifier.create(keyAuthFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isTrue();
    }
}
