package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.GatewayFilter;
import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * {@link GatewayFilter} implementation that authenticates inbound requests against
 * tenant-scoped consumer API keys via {@link ConsumerKeyLookup}.
 */
@Component
public class KeyAuthFilter implements GatewayFilter {

    public static final String PLUGIN_NAME = "key-auth";
    public static final String ATTR_CONSUMER_IDENTITY = "gateway.consumer.identity";
    public static final String ATTR_CONSUMER_ID = "gateway.consumer.id";
    public static final String ATTR_TENANT_ID = "gateway.tenant.id";

    private final ConsumerKeyLookup consumerKeyLookup;
    private final List<String> defaultKeyNames;
    private final boolean defaultHideCredentials;

    @org.springframework.beans.factory.annotation.Autowired
    public KeyAuthFilter(ConsumerKeyLookup consumerKeyLookup) {
        this(consumerKeyLookup, KeyAuthConfigSchema.DEFAULT_KEY_NAMES, false);
    }

    public KeyAuthFilter(ConsumerKeyLookup consumerKeyLookup, List<String> defaultKeyNames, boolean defaultHideCredentials) {
        this.consumerKeyLookup = consumerKeyLookup;
        this.defaultKeyNames = defaultKeyNames != null ? defaultKeyNames : KeyAuthConfigSchema.DEFAULT_KEY_NAMES;
        this.defaultHideCredentials = defaultHideCredentials;
    }

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Map<String, Object> configMap = resolveConfig(exchange);
        List<String> keyNames = configMap != null
                ? KeyAuthConfigSchema.extractKeyNames(configMap)
                : defaultKeyNames;
        boolean hideCredentials = configMap != null
                ? KeyAuthConfigSchema.shouldHideCredentials(configMap)
                : defaultHideCredentials;

        String rawKey = extractApiKey(exchange, keyNames);
        if (rawKey == null || rawKey.isBlank()) {
            return rejectUnauthorized(exchange);
        }

        return consumerKeyLookup.lookup(rawKey)
                .switchIfEmpty(Mono.defer(() -> rejectUnauthorized(exchange).then(Mono.empty())))
                .flatMap(identity -> {
                    // Attach identity and tenant to exchange attributes
                    exchange.getAttributes().put(ATTR_CONSUMER_IDENTITY, identity);
                    exchange.getAttributes().put(ATTR_CONSUMER_ID, identity.consumerId());
                    exchange.getAttributes().put(ATTR_TENANT_ID, identity.tenantId());
                    exchange.getAttributes().put("consumer_id", identity.consumerId());
                    exchange.getAttributes().put("tenant_id", identity.tenantId());

                    ServerHttpRequest.Builder reqBuilder = exchange.getRequest().mutate();
                    if (identity.consumerId() != null) {
                        reqBuilder.header("X-Consumer-Id", identity.consumerId().toString());
                    }
                    if (identity.tenantId() != null) {
                        reqBuilder.header("X-Tenant-Id", identity.tenantId().toString());
                    }

                    if (hideCredentials) {
                        for (String keyName : keyNames) {
                            reqBuilder.headers(headers -> headers.remove(keyName));
                        }
                    }

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(reqBuilder.build())
                            .build();

                    return chain.filter(mutatedExchange);
                });
    }

    private String extractApiKey(ServerWebExchange exchange, List<String> keyNames) {
        for (String name : keyNames) {
            String value = exchange.getRequest().getHeaders().getFirst(name);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private Mono<Void> rejectUnauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveConfig(ServerWebExchange exchange) {
        Object direct = exchange.getAttribute("plugin_config_" + PLUGIN_NAME);
        if (direct instanceof PluginConfig pc) {
            return pc.getConfig();
        }
        if (direct instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}
