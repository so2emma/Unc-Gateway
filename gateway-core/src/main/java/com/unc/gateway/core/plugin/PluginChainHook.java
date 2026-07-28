package com.unc.gateway.core.plugin;

import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import com.unc.gateway.plugins.api.PluginRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Component
public class PluginChainHook {

    private final PluginRegistry pluginRegistry;

    public PluginChainHook(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    public Mono<ResponseEntity<byte[]>> executeChain(
            ServerWebExchange exchange,
            List<PluginConfig> configs,
            Supplier<Mono<ResponseEntity<byte[]>>> proxyCall
    ) {
        List<PluginConfig> activeConfigs = configs != null ? configs : Collections.emptyList();
        GatewayFilterChain filterChain = pluginRegistry.resolveChain(activeConfigs);

        return filterChain.filter(exchange).then(proxyCall.get());
    }
}
