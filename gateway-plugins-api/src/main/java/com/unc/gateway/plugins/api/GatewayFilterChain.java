package com.unc.gateway.plugins.api;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Represents a reactive filter chain that delegates to subsequent filters or the target endpoint.
 */
public interface GatewayFilterChain {
    /**
     * Delegates to the next filter in the chain.
     *
     * @param exchange the current server web exchange
     * @return a {@link Mono<Void>} indicating completion
     */
    Mono<Void> filter(ServerWebExchange exchange);
}
