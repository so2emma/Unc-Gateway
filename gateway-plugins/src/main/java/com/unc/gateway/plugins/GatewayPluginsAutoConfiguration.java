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
    @ConditionalOnMissingBean
    public JwtVerifier jwtVerifier() {
        return new JwtVerifier();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthFilter jwtAuthFilter(JwtVerifier jwtVerifier) {
        return new JwtAuthFilter(jwtVerifier);
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestTransformFilter requestTransformFilter() {
        return new RequestTransformFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public LoggingFilter loggingFilter(@Autowired(required = false) LoggingFilter.LogSink logSink) {
        return logSink != null ? new LoggingFilter(logSink) : new LoggingFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public com.unc.gateway.plugins.oauth2.JwksKeyCache jwksKeyCache() {
        return new com.unc.gateway.plugins.oauth2.JwksKeyCache();
    }

    @Bean
    @ConditionalOnMissingBean
    public com.unc.gateway.plugins.oauth2.JwksJwtValidator jwksJwtValidator(
            com.unc.gateway.plugins.oauth2.JwksKeyCache jwksKeyCache) {
        return new com.unc.gateway.plugins.oauth2.JwksJwtValidator(jwksKeyCache);
    }

    @Bean
    @ConditionalOnMissingBean
    public com.unc.gateway.plugins.oauth2.TokenIntrospectionClient tokenIntrospectionClient() {
        return new com.unc.gateway.plugins.oauth2.TokenIntrospectionClient();
    }

    @Bean
    @ConditionalOnMissingBean
    public com.unc.gateway.plugins.oauth2.ScopeEnforcer scopeEnforcer() {
        return new com.unc.gateway.plugins.oauth2.ScopeEnforcer();
    }

    @Bean
    @ConditionalOnMissingBean
    public com.unc.gateway.plugins.oauth2.OAuth2OidcFilter oauth2OidcFilter(
            com.unc.gateway.plugins.oauth2.JwksJwtValidator jwksJwtValidator,
            com.unc.gateway.plugins.oauth2.TokenIntrospectionClient tokenIntrospectionClient,
            com.unc.gateway.plugins.oauth2.ScopeEnforcer scopeEnforcer) {
        return new com.unc.gateway.plugins.oauth2.OAuth2OidcFilter(jwksJwtValidator, tokenIntrospectionClient, scopeEnforcer);
    }

    @Bean
    public PluginRegistrationInitializer pluginRegistrationInitializer(
            @Autowired(required = false) PluginRegistry registry,
            @Autowired(required = false) KeyAuthFilter keyAuthFilter,
            @Autowired(required = false) RateLimitFilter rateLimitFilter,
            @Autowired(required = false) JwtAuthFilter jwtAuthFilter,
            @Autowired(required = false) RequestTransformFilter requestTransformFilter,
            @Autowired(required = false) LoggingFilter loggingFilter,
            @Autowired(required = false) com.unc.gateway.plugins.oauth2.OAuth2OidcFilter oauth2OidcFilter) {
        return new PluginRegistrationInitializer(registry, keyAuthFilter, rateLimitFilter, jwtAuthFilter, requestTransformFilter, loggingFilter, oauth2OidcFilter);
    }

    public static class PluginRegistrationInitializer {
        public PluginRegistrationInitializer(
                PluginRegistry registry,
                KeyAuthFilter keyAuthFilter,
                RateLimitFilter rateLimitFilter,
                JwtAuthFilter jwtAuthFilter,
                RequestTransformFilter requestTransformFilter) {
            this(registry, keyAuthFilter, rateLimitFilter, jwtAuthFilter, requestTransformFilter, null, null);
        }

        public PluginRegistrationInitializer(
                PluginRegistry registry,
                KeyAuthFilter keyAuthFilter,
                RateLimitFilter rateLimitFilter,
                JwtAuthFilter jwtAuthFilter,
                RequestTransformFilter requestTransformFilter,
                LoggingFilter loggingFilter) {
            this(registry, keyAuthFilter, rateLimitFilter, jwtAuthFilter, requestTransformFilter, loggingFilter, null);
        }

        public PluginRegistrationInitializer(
                PluginRegistry registry,
                KeyAuthFilter keyAuthFilter,
                RateLimitFilter rateLimitFilter,
                JwtAuthFilter jwtAuthFilter,
                RequestTransformFilter requestTransformFilter,
                LoggingFilter loggingFilter,
                com.unc.gateway.plugins.oauth2.OAuth2OidcFilter oauth2OidcFilter) {
            if (registry != null) {
                if (keyAuthFilter != null && !registry.isRegistered("key-auth")) {
                    registry.register("key-auth", keyAuthFilter, new KeyAuthConfigSchema());
                }
                if (rateLimitFilter != null && !registry.isRegistered("rate-limit")) {
                    registry.register("rate-limit", rateLimitFilter, new RateLimitConfigSchema());
                }
                if (jwtAuthFilter != null && !registry.isRegistered("jwt-auth")) {
                    registry.register("jwt-auth", jwtAuthFilter, new JwtAuthConfigSchema());
                }
                if (requestTransformFilter != null && !registry.isRegistered("request-transform")) {
                    registry.register("request-transform", requestTransformFilter, new RequestTransformConfigSchema());
                }
                if (loggingFilter != null && !registry.isRegistered("logging")) {
                    registry.register("logging", loggingFilter, new LoggingFilterConfig());
                }
                if (oauth2OidcFilter != null && !registry.isRegistered("oauth2-oidc")) {
                    registry.register("oauth2-oidc", oauth2OidcFilter, new com.unc.gateway.plugins.oauth2.OAuth2OidcConfigSchema());
                }
            }
        }
    }
}
