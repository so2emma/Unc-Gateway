package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.GatewayFilter;
import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * {@link GatewayFilter} implementation that authenticates inbound requests by verifying
 * a signed JSON Web Token presented in the configured header (default {@code Authorization: Bearer <token>}).
 */
@Component
public class JwtAuthFilter implements GatewayFilter {

    public static final String PLUGIN_NAME = "jwt-auth";
    public static final String ATTR_JWT_CLAIMS = "gateway.jwt.claims";
    public static final String ATTR_JWT_SUBJECT = "gateway.jwt.subject";

    private final JwtVerifier jwtVerifier;
    private final String defaultSecret;
    private final String defaultAlgorithm;
    private final String defaultHeaderName;

    @Autowired
    public JwtAuthFilter(JwtVerifier jwtVerifier) {
        this(jwtVerifier, null, JwtAuthConfigSchema.DEFAULT_ALGORITHM, JwtAuthConfigSchema.DEFAULT_HEADER_NAME);
    }

    public JwtAuthFilter(JwtVerifier jwtVerifier, String defaultSecret, String defaultAlgorithm, String defaultHeaderName) {
        this.jwtVerifier = jwtVerifier != null ? jwtVerifier : new JwtVerifier();
        this.defaultSecret = defaultSecret;
        this.defaultAlgorithm = defaultAlgorithm != null ? defaultAlgorithm : JwtAuthConfigSchema.DEFAULT_ALGORITHM;
        this.defaultHeaderName = defaultHeaderName != null ? defaultHeaderName : JwtAuthConfigSchema.DEFAULT_HEADER_NAME;
    }

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Map<String, Object> config = resolveConfig(exchange);

        String headerName = config != null
                ? JwtAuthConfigSchema.extractHeaderName(config)
                : defaultHeaderName;

        String rawHeaderValue = exchange.getRequest().getHeaders().getFirst(headerName);
        if (rawHeaderValue == null || rawHeaderValue.isBlank()) {
            return rejectUnauthorized(exchange);
        }

        String token = extractBearerToken(rawHeaderValue, headerName);
        if (token == null || token.isBlank()) {
            return rejectUnauthorized(exchange);
        }

        String secret = config != null ? JwtAuthConfigSchema.extractSecret(config) : defaultSecret;
        if (secret == null || secret.isBlank()) {
            secret = config != null ? JwtAuthConfigSchema.extractPublicKey(config) : null;
        }
        if (secret == null || secret.isBlank()) {
            return rejectUnauthorized(exchange);
        }

        String algorithm = config != null ? JwtAuthConfigSchema.extractAlgorithm(config) : defaultAlgorithm;

        Optional<Map<String, Object>> claimsOpt = jwtVerifier.verify(token, secret, algorithm);
        if (claimsOpt.isEmpty()) {
            return rejectUnauthorized(exchange);
        }

        Map<String, Object> claims = claimsOpt.get();

        // Attach claims to exchange attributes
        exchange.getAttributes().put(ATTR_JWT_CLAIMS, claims);
        exchange.getAttributes().put("jwt_claims", claims);

        ServerHttpRequest.Builder reqBuilder = exchange.getRequest().mutate();

        if (claims.containsKey("sub") && claims.get("sub") != null) {
            String subject = claims.get("sub").toString();
            exchange.getAttributes().put(ATTR_JWT_SUBJECT, subject);
            exchange.getAttributes().put("jwt_sub", subject);
            exchange.getAttributes().put("consumer_id", subject);
            reqBuilder.header("X-Consumer-Id", subject);
        }

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(reqBuilder.build())
                .build();

        return chain.filter(mutatedExchange);
    }

    private String extractBearerToken(String headerValue, String headerName) {
        String trimmed = headerValue.trim();
        if (JwtAuthConfigSchema.DEFAULT_HEADER_NAME.equalsIgnoreCase(headerName)) {
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
