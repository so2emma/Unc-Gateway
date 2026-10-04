package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTransformFilterTest {

    private RequestTransformFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestTransformFilter();
    }

    @Test
    @DisplayName("RequestTransformFilter: given addHeaders, outbound request carries added header/value pairs")
    void testAddHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("Existing-Header", "existing-value")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        PluginConfig config = new PluginConfig();
        config.setName("request-transform");
        config.setConfig(Map.of(
                "addHeaders", Map.of(
                        "X-Gateway-Trace", "unc-gateway",
                        "X-Custom-Env", "staging"
                )
        ));
        exchange.getAttributes().put("plugin_config_request-transform", config);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamCalled.set(true);
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("Existing-Header"))
                    .isEqualTo("existing-value");
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-Gateway-Trace"))
                    .isEqualTo("unc-gateway");
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-Custom-Env"))
                    .isEqualTo("staging");
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(downstreamCalled.get()).isTrue();
    }

    @Test
    @DisplayName("RequestTransformFilter: given removeHeaders, header present on inbound request is absent on outbound")
    void testRemoveHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("X-Remove-Me", "secret")
                .header("X-Keep-Me", "visible")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        PluginConfig config = new PluginConfig();
        config.setName("request-transform");
        config.setConfig(Map.of(
                "removeHeaders", List.of("X-Remove-Me")
        ));
        exchange.getAttributes().put("plugin_config_request-transform", config);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamCalled.set(true);
            assertThat(mutatedExchange.getRequest().getHeaders().containsKey("X-Remove-Me")).isFalse();
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-Keep-Me")).isEqualTo("visible");
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(downstreamCalled.get()).isTrue();
    }

    @Test
    @DisplayName("RequestTransformFilter: given renameHeaders, value is preserved under new name and old name is absent")
    void testRenameHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("X-Old-Name", "val123")
                .header("Other-Header", "other")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        PluginConfig config = new PluginConfig();
        config.setName("request-transform");
        config.setConfig(Map.of(
                "renameHeaders", Map.of("X-Old-Name", "X-New-Name")
        ));
        exchange.getAttributes().put("plugin_config_request-transform", config);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamCalled.set(true);
            assertThat(mutatedExchange.getRequest().getHeaders().containsKey("X-Old-Name")).isFalse();
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-New-Name")).isEqualTo("val123");
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("Other-Header")).isEqualTo("other");
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(downstreamCalled.get()).isTrue();
    }

    @Test
    @DisplayName("RequestTransformFilter: combined add, remove, and rename operations applied in order")
    void testCombinedOperations() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("X-Old-Token", "token-xyz")
                .header("X-Internal-Debug", "true")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        PluginConfig config = new PluginConfig();
        config.setName("request-transform");
        config.setConfig(Map.of(
                "renameHeaders", Map.of("X-Old-Token", "X-Auth-Token"),
                "removeHeaders", List.of("X-Internal-Debug"),
                "addHeaders", Map.of("X-Forwarded-By", "unc-gateway")
        ));
        exchange.getAttributes().put("plugin_config_request-transform", config);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamCalled.set(true);
            assertThat(mutatedExchange.getRequest().getHeaders().containsKey("X-Old-Token")).isFalse();
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-Auth-Token")).isEqualTo("token-xyz");
            assertThat(mutatedExchange.getRequest().getHeaders().containsKey("X-Internal-Debug")).isFalse();
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-Forwarded-By")).isEqualTo("unc-gateway");
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(downstreamCalled.get()).isTrue();
    }

    @Test
    @DisplayName("RequestTransformFilter: snake_case config keys supported")
    void testSnakeCaseConfigKeys() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("X-Deprecated", "old")
                .header("X-Delete", "trash")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        PluginConfig config = new PluginConfig();
        config.setName("request-transform");
        config.setConfig(Map.of(
                "rename_headers", Map.of("X-Deprecated", "X-Modern"),
                "remove_headers", List.of("X-Delete"),
                "add_headers", Map.of("X-Injected", "injected-value")
        ));
        exchange.getAttributes().put("plugin_config_request-transform", config);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamCalled.set(true);
            assertThat(mutatedExchange.getRequest().getHeaders().containsKey("X-Deprecated")).isFalse();
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-Modern")).isEqualTo("old");
            assertThat(mutatedExchange.getRequest().getHeaders().containsKey("X-Delete")).isFalse();
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-Injected")).isEqualTo("injected-value");
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(downstreamCalled.get()).isTrue();
    }

    @Test
    @DisplayName("RequestTransformFilter: empty or null config passes request through unchanged")
    void testEmptyConfigPassesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/hello")
                .header("Original-Header", "val")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            downstreamCalled.set(true);
            assertThat(ex.getRequest().getHeaders().getFirst("Original-Header")).isEqualTo("val");
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(downstreamCalled.get()).isTrue();
    }
}
