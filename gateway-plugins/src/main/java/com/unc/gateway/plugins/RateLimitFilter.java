package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.GatewayFilter;
import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

/**
 * {@link GatewayFilter} implementation that enforces a sliding-window rate limit
 * per consumer via {@link RedisSlidingWindowRateLimiter}.
 */
@Component
public class RateLimitFilter implements GatewayFilter {

    public static final String PLUGIN_NAME = "rate-limit";
    public static final String HEADER_RETRY_AFTER = HttpHeaders.RETRY_AFTER;
    public static final String HEADER_RATELIMIT_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_RATELIMIT_REMAINING = "X-RateLimit-Remaining";

    private final RedisSlidingWindowRateLimiter rateLimiter;
    private final Long defaultLimit;
    private final Long defaultWindowSeconds;

    @org.springframework.beans.factory.annotation.Autowired
    public RateLimitFilter(RedisSlidingWindowRateLimiter rateLimiter) {
        this(rateLimiter, null, null);
    }

    public RateLimitFilter(RedisSlidingWindowRateLimiter rateLimiter, Long defaultLimit, Long defaultWindowSeconds) {
        this.rateLimiter = rateLimiter;
        this.defaultLimit = defaultLimit;
        this.defaultWindowSeconds = defaultWindowSeconds;
    }

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Map<String, Object> config = resolveConfig(exchange);

        long limit;
        long windowSeconds;
        try {
            if (config != null) {
                limit = RateLimitConfigSchema.extractLimit(config);
                windowSeconds = RateLimitConfigSchema.extractWindowSeconds(config);
            } else if (defaultLimit != null && defaultWindowSeconds != null) {
                limit = defaultLimit;
                windowSeconds = defaultWindowSeconds;
            } else {
                // No configuration found for rate-limit, pass through
                return chain.filter(exchange);
            }
        } catch (IllegalArgumentException ex) {
            // If config is present but malformed, pass through or throw
            return chain.filter(exchange);
        }

        String consumerKey = resolveConsumerKey(exchange);

        return rateLimiter.tryAcquire(consumerKey, limit, windowSeconds)
                .flatMap(result -> {
                    if (result.allowed()) {
                        exchange.getResponse().getHeaders().set(HEADER_RATELIMIT_LIMIT, String.valueOf(limit));
                        exchange.getResponse().getHeaders().set(HEADER_RATELIMIT_REMAINING, String.valueOf(result.remaining()));
                        return chain.filter(exchange);
                    } else {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().set(HEADER_RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
                        exchange.getResponse().getHeaders().set(HEADER_RATELIMIT_LIMIT, String.valueOf(limit));
                        exchange.getResponse().getHeaders().set(HEADER_RATELIMIT_REMAINING, "0");
                        return exchange.getResponse().setComplete();
                    }
                });
    }

    private String resolveConsumerKey(ServerWebExchange exchange) {
        Object consumerIdObj = exchange.getAttribute(KeyAuthFilter.ATTR_CONSUMER_ID);
        if (consumerIdObj == null) {
            consumerIdObj = exchange.getAttribute("consumer_id");
        }
        if (consumerIdObj != null) {
            return consumerIdObj.toString();
        }

        String consumerIdHdr = exchange.getRequest().getHeaders().getFirst("X-Consumer-Id");
        if (consumerIdHdr != null && !consumerIdHdr.isBlank()) {
            return consumerIdHdr.trim();
        }

        Object tenantObj = exchange.getAttribute(KeyAuthFilter.ATTR_TENANT_ID);
        if (tenantObj == null) {
            tenantObj = exchange.getAttribute("tenant_id");
        }
        String tenantStr = tenantObj != null ? tenantObj.toString() : "anonymous";

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        String ip = remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";

        return tenantStr + ":" + ip;
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
