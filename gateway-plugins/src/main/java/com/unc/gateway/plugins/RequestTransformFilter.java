package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.GatewayFilter;
import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * {@link GatewayFilter} implementation that mutates an inbound request (adding, removing,
 * or renaming headers) per tenant-scoped configuration before forwarding upstream.
 */
@Component
public class RequestTransformFilter implements GatewayFilter {

    public static final String PLUGIN_NAME = "request-transform";
    public static final String ATTR_TRANSFORMED_HEADERS = "gateway.transformed.headers";

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Map<String, Object> config = resolveConfig(exchange);
        if (config == null || config.isEmpty()) {
            return chain.filter(exchange);
        }

        Map<String, String> renameHeaders = RequestTransformConfigSchema.extractRenameHeaders(config);
        List<String> removeHeaders = RequestTransformConfigSchema.extractRemoveHeaders(config);
        Map<String, String> addHeaders = RequestTransformConfigSchema.extractAddHeaders(config);

        if (renameHeaders.isEmpty() && removeHeaders.isEmpty() && addHeaders.isEmpty()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest.Builder reqBuilder = exchange.getRequest().mutate();
        HttpHeaders currentHeaders = exchange.getRequest().getHeaders();

        // 1. Rename headers
        if (!renameHeaders.isEmpty()) {
            for (Map.Entry<String, String> entry : renameHeaders.entrySet()) {
                String oldName = entry.getKey();
                String newName = entry.getValue();
                List<String> values = currentHeaders.get(oldName);
                if (values != null && !values.isEmpty()) {
                    reqBuilder.headers(h -> {
                        h.remove(oldName);
                        h.addAll(newName, values);
                    });
                }
            }
        }

        // 2. Remove headers
        if (!removeHeaders.isEmpty()) {
            for (String toRemove : removeHeaders) {
                reqBuilder.headers(h -> h.remove(toRemove));
            }
        }

        // 3. Add headers
        if (!addHeaders.isEmpty()) {
            for (Map.Entry<String, String> entry : addHeaders.entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();
                reqBuilder.header(name, value);
            }
        }

        ServerHttpRequest mutatedRequest = reqBuilder.build();
        exchange.getAttributes().put(ATTR_TRANSFORMED_HEADERS, mutatedRequest.getHeaders());

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        return chain.filter(mutatedExchange);
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
