package com.unc.gateway.plugins.oauth2;

import com.unc.gateway.plugins.api.GatewayFilter;
import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * {@link GatewayFilter} implementation providing enterprise OAuth2 / OIDC authentication and scope enforcement.
 * <p>
 * Supports both JWKS-based JWT signature and claims validation ({@code mode: jwks}) and RFC 7662
 * token introspection ({@code mode: introspect}). Enforces route/tenant {@code requiredScopes} and
 * propagates authenticated consumer identity and scopes to downstream plugins and upstream services.
 */
@Component
public class OAuth2OidcFilter implements GatewayFilter {

    public static final String PLUGIN_NAME = "oauth2-oidc";
    public static final String ATTR_OAUTH2_CLAIMS = "gateway.oauth2.claims";
    public static final String ATTR_OAUTH2_SUBJECT = "gateway.oauth2.subject";
    public static final String ATTR_OAUTH2_SCOPES = "gateway.oauth2.scopes";

    private final JwksJwtValidator jwksJwtValidator;
    private final TokenIntrospectionClient tokenIntrospectionClient;
    private final ScopeEnforcer scopeEnforcer;

    @Autowired
    public OAuth2OidcFilter(
            JwksJwtValidator jwksJwtValidator,
            TokenIntrospectionClient tokenIntrospectionClient,
            ScopeEnforcer scopeEnforcer
    ) {
        this.jwksJwtValidator = jwksJwtValidator;
        this.tokenIntrospectionClient = tokenIntrospectionClient;
        this.scopeEnforcer = scopeEnforcer != null ? scopeEnforcer : new ScopeEnforcer();
    }

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Map<String, Object> config = resolveConfig(exchange);

        String headerName = config != null
                ? OAuth2OidcConfigSchema.extractHeaderName(config)
                : OAuth2OidcConfigSchema.DEFAULT_HEADER_NAME;

        String rawHeaderValue = exchange.getRequest().getHeaders().getFirst(headerName);
        if (rawHeaderValue == null || rawHeaderValue.isBlank()) {
            return rejectUnauthorized(exchange);
        }

        String token = extractBearerToken(rawHeaderValue, headerName);
        if (token == null || token.isBlank()) {
            return rejectUnauthorized(exchange);
        }

        String mode = config != null
                ? OAuth2OidcConfigSchema.extractMode(config)
                : OAuth2OidcConfigSchema.DEFAULT_MODE;

        Mono<Map<String, Object>> claimsMono = OAuth2OidcConfigSchema.MODE_INTROSPECT.equalsIgnoreCase(mode)
                ? tokenIntrospectionClient.introspect(token, config)
                : jwksJwtValidator.validate(token, config);

        return claimsMono
                .switchIfEmpty(Mono.defer(() -> rejectUnauthorized(exchange).then(Mono.empty())))
                .flatMap(claims -> {
                    // Enforce required scopes
                    List<String> requiredScopes = config != null
                            ? OAuth2OidcConfigSchema.extractRequiredScopes(config)
                            : Collections.emptyList();

                    ScopeEnforcer.ScopeEnforcementResult scopeResult = scopeEnforcer.enforce(requiredScopes, claims);
                    if (!scopeResult.isGranted()) {
                        return rejectInsufficientScope(exchange, scopeResult);
                    }

                    // Attach claims to exchange and headers
                    ServerWebExchange mutatedExchange = attachClaimsAndMutate(exchange, claims, scopeResult.getTokenScopes());
                    return chain.filter(mutatedExchange);
                });
    }

    private ServerWebExchange attachClaimsAndMutate(
            ServerWebExchange exchange,
            Map<String, Object> claims,
            Set<String> tokenScopes
    ) {
        exchange.getAttributes().put(ATTR_OAUTH2_CLAIMS, claims);
        exchange.getAttributes().put("oauth2_claims", claims);
        exchange.getAttributes().put("jwt_claims", claims);

        ServerHttpRequest.Builder reqBuilder = exchange.getRequest().mutate();

        if (claims.containsKey("tenant_id") && claims.get("tenant_id") != null) {
            exchange.getAttributes().put("tenant_id", claims.get("tenant_id"));
        }

        if (claims.containsKey("sub") && claims.get("sub") != null) {
            String subject = claims.get("sub").toString();
            exchange.getAttributes().put(ATTR_OAUTH2_SUBJECT, subject);
            exchange.getAttributes().put("jwt_sub", subject);
            exchange.getAttributes().put("consumer_id", subject);
            reqBuilder.header("X-Consumer-Id", subject);
        }

        if (!tokenScopes.isEmpty()) {
            String scopeStr = String.join(" ", tokenScopes);
            exchange.getAttributes().put(ATTR_OAUTH2_SCOPES, tokenScopes);
            exchange.getAttributes().put("oauth2_scope", scopeStr);
            exchange.getAttributes().put("scope", scopeStr);
            reqBuilder.header("X-OAuth-Scope", scopeStr);
        }

        return exchange.mutate()
                .request(reqBuilder.build())
                .build();
    }

    private String extractBearerToken(String headerValue, String headerName) {
        String trimmed = headerValue.trim();
        if (OAuth2OidcConfigSchema.DEFAULT_HEADER_NAME.equalsIgnoreCase(headerName)) {
            if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return null;
            }
            return trimmed.substring(7).trim();
        } else {
            if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return trimmed.substring(7).trim();
            }
            return trimmed;
        }
    }

    private Mono<Void> rejectUnauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> rejectInsufficientScope(
            ServerWebExchange exchange,
            ScopeEnforcer.ScopeEnforcementResult scopeResult
    ) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
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
