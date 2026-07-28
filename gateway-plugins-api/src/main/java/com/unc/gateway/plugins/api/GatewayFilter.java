package com.unc.gateway.plugins.api;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Interface contract implemented by all API Gateway plugins.
 */
public interface GatewayFilter {
    /**
     * Process the web request and (optionally) delegate to the next filter in the chain.
     *
     * @param exchange the current server web exchange
     * @param chain    the filter chain to delegate to
     * @return a {@link Mono<Void>} indicating completion
     */
    Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain);

    /**
     * Returns the unique name identifier of this plugin.
     *
     * @return plugin name
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
