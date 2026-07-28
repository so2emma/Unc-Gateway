package com.unc.gateway.plugins.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class PluginRegistryTest {

    private PluginRegistry registry;
    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        registry = new PluginRegistry();
        exchange = Mockito.mock(ServerWebExchange.class);
    }

    @Test
    @DisplayName("PluginRegistry.register - should successfully register and resolve filter by name")
    void testRegisterAndGetFilter() {
        GatewayFilter filter = (exchange, chain) -> chain.filter(exchange);

        registry.register("key-auth", filter);

        assertThat(registry.isRegistered("key-auth")).isTrue();
        assertThat(registry.getFilter("key-auth")).isSameAs(filter);
    }

    @Test
    @DisplayName("PluginRegistry.register - duplicate registration should throw IllegalArgumentException")
    void testRegisterDuplicateFilterThrows() {
        GatewayFilter filter1 = (exchange, chain) -> chain.filter(exchange);
        GatewayFilter filter2 = (exchange, chain) -> chain.filter(exchange);

        registry.register("rate-limit", filter1);

        assertThatThrownBy(() -> registry.register("rate-limit", filter2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plugin already registered: rate-limit");
    }

    @Test
    @DisplayName("PluginRegistry.resolveChain - should execute filters in order and skip disabled filters")
    void testResolveChainOrderingAndDisabledSkipping() {
        List<String> executionOrder = new ArrayList<>();

        GatewayFilter filterA = (ex, chain) -> {
            executionOrder.add("FilterA");
            return chain.filter(ex);
        };
        GatewayFilter filterB = (ex, chain) -> {
            executionOrder.add("FilterB");
            return chain.filter(ex);
        };
        GatewayFilter filterC = (ex, chain) -> {
            executionOrder.add("FilterC");
            return chain.filter(ex);
        };

        registry.register("plugin-a", filterA);
        registry.register("plugin-b", filterB);
        registry.register("plugin-c", filterC);

        PluginConfig configA = new PluginConfig("1", "tenant-1", "plugin-a", 10, true, Collections.emptyMap());
        PluginConfig configB = new PluginConfig("2", "tenant-1", "plugin-b", 5, false, Collections.emptyMap()); // disabled
        PluginConfig configC = new PluginConfig("3", "tenant-1", "plugin-c", 1, true, Collections.emptyMap());

        // Order ascending: plugin-c (order 1), plugin-a (order 10). plugin-b (order 5) is disabled so skipped.
        GatewayFilterChain chain = registry.resolveChain(Arrays.asList(configA, configB, configC));

        StepVerifier.create(chain.filter(exchange))
                .verifyComplete();

        assertThat(executionOrder).containsExactly("FilterC", "FilterA");
    }

    @Test
    @DisplayName("PluginRegistry.resolveChain - unknown plugin name should throw IllegalArgumentException")
    void testResolveChainUnknownPluginThrows() {
        GatewayFilter filter = (ex, chain) -> chain.filter(ex);
        registry.register("key-auth", filter);

        PluginConfig unknownConfig = new PluginConfig("1", "tenant-1", "unknown-plugin", 1, true, Collections.emptyMap());

        assertThatThrownBy(() -> registry.resolveChain(Collections.singletonList(unknownConfig)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown plugin: unknown-plugin");
    }

    @Test
    @DisplayName("PluginConfig validation - schema validation should succeed or reject invalid payloads")
    void testPluginConfigValidation() {
        GatewayFilter filter = (ex, chain) -> chain.filter(ex);
        ConfigValidator validator = config -> {
            if (config == null || !config.containsKey("apiKeyHeader")) {
                throw new IllegalArgumentException("Missing required config field: apiKeyHeader");
            }
        };

        registry.register("key-auth", filter, validator);

        // Valid payload
        PluginConfig validConfig = new PluginConfig("1", "tenant-1", "key-auth", 1, true, Map.of("apiKeyHeader", "X-API-Key"));
        assertThatCode(() -> registry.validateConfig(validConfig)).doesNotThrowAnyException();

        // Invalid payload
        PluginConfig invalidConfig = new PluginConfig("2", "tenant-1", "key-auth", 1, true, Map.of("wrongField", "value"));
        assertThatThrownBy(() -> registry.validateConfig(invalidConfig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required config field: apiKeyHeader");
    }

    @Test
    @DisplayName("GatewayFilter default/no-op behavior - empty chain passes exchange through unchanged")
    void testEmptyChainPassesThrough() {
        GatewayFilterChain chain = registry.resolveChain(Collections.emptyList());

        StepVerifier.create(chain.filter(exchange))
                .verifyComplete();
    }
}
