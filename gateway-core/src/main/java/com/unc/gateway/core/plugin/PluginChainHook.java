package com.unc.gateway.core.plugin;

import com.unc.gateway.plugins.PluginConfigLoader;
import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import com.unc.gateway.plugins.api.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class PluginChainHook {

    private static final Logger log = LoggerFactory.getLogger(PluginChainHook.class);

    private final PluginRegistry pluginRegistry;
    private final PluginConfigLoader pluginConfigLoader;

    public PluginChainHook(PluginRegistry pluginRegistry) {
        this(pluginRegistry, null);
    }

    @Autowired
    public PluginChainHook(PluginRegistry pluginRegistry,
                           @Autowired(required = false) PluginConfigLoader pluginConfigLoader) {
        this.pluginRegistry = pluginRegistry;
        this.pluginConfigLoader = pluginConfigLoader;
    }

    public Mono<ResponseEntity<byte[]>> executeChain(
            ServerWebExchange exchange,
            List<PluginConfig> configs,
            Supplier<Mono<ResponseEntity<byte[]>>> proxyCall
    ) {
        List<PluginConfig> activeConfigs;
        if (configs != null && !configs.isEmpty()) {
            activeConfigs = configs;
        } else if (pluginConfigLoader != null) {
            UUID tenantId = resolveTenantId(exchange);
            activeConfigs = pluginConfigLoader.getCachedConfigs(tenantId);
        } else {
            activeConfigs = Collections.emptyList();
        }

        for (PluginConfig pc : activeConfigs) {
            if (pc != null && pc.getName() != null) {
                exchange.getAttributes().put("plugin_config_" + pc.getName(), pc);
            }
        }

        GatewayFilterChain filterChain = pluginRegistry.resolveChain(activeConfigs);

        return filterChain.filter(exchange)
                .then(Mono.defer(() -> {
                    if (exchange.getResponse().getStatusCode() != null) {
                        ResponseEntity.BodyBuilder builder = ResponseEntity.status(exchange.getResponse().getStatusCode());
                        exchange.getResponse().getHeaders().forEach((key, values) -> {
                            builder.header(key, values.toArray(new String[0]));
                        });
                        return Mono.just(builder.build());
                    }
                    return proxyCall.get();
                }));
    }

    private UUID resolveTenantId(ServerWebExchange exchange) {
        Object attrTenant = exchange.getAttribute("tenant_id");
        if (attrTenant instanceof UUID u) {
            return u;
        }
        if (attrTenant instanceof String s && !s.isBlank()) {
            try {
                return UUID.fromString(s.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        String headerTenant = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        if (headerTenant != null && !headerTenant.isBlank()) {
            try {
                return UUID.fromString(headerTenant.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }
}
