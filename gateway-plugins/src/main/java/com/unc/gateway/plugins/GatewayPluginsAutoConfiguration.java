package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.PluginRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Spring Boot auto-configuration for {@code gateway-plugins}.
 * Discovers and registers {@code key-auth} and {@code rate-limit} filters into {@link PluginRegistry}.
 */
@AutoConfiguration
public class GatewayPluginsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConsumerKeyLookup consumerKeyLookup(@Autowired(required = false) DatabaseClient databaseClient) {
        return databaseClient != null ? new ConsumerKeyLookup(databaseClient) : null;
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisSlidingWindowRateLimiter redisSlidingWindowRateLimiter(
            @Autowired(required = false) ReactiveRedisTemplate<String, String> redisTemplate) {
        return new RedisSlidingWindowRateLimiter(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginConfigLoader pluginConfigLoader(@Autowired(required = false) DatabaseClient databaseClient) {
        return databaseClient != null ? new PluginConfigLoader(databaseClient) : null;
    }

    @Bean
    @ConditionalOnMissingBean
    public KeyAuthFilter keyAuthFilter(@Autowired(required = false) ConsumerKeyLookup consumerKeyLookup) {
        return consumerKeyLookup != null ? new KeyAuthFilter(consumerKeyLookup) : null;
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitFilter rateLimitFilter(RedisSlidingWindowRateLimiter rateLimiter) {
        return new RateLimitFilter(rateLimiter);
    }

    @Bean
    public PluginRegistrationInitializer pluginRegistrationInitializer(
            @Autowired(required = false) PluginRegistry registry,
            @Autowired(required = false) KeyAuthFilter keyAuthFilter,
            @Autowired(required = false) RateLimitFilter rateLimitFilter) {
        return new PluginRegistrationInitializer(registry, keyAuthFilter, rateLimitFilter);
    }

    public static class PluginRegistrationInitializer {
        public PluginRegistrationInitializer(PluginRegistry registry, KeyAuthFilter keyAuthFilter, RateLimitFilter rateLimitFilter) {
            if (registry != null) {
                if (keyAuthFilter != null && !registry.isRegistered("key-auth")) {
                    registry.register("key-auth", keyAuthFilter, new KeyAuthConfigSchema());
                }
                if (rateLimitFilter != null && !registry.isRegistered("rate-limit")) {
                    registry.register("rate-limit", rateLimitFilter, new RateLimitConfigSchema());
                }
            }
        }
    }
}
