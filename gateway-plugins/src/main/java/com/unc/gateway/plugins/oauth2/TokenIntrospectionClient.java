package com.unc.gateway.plugins.oauth2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reactive RFC 7662-compliant Token Introspection client.
 * <p>
 * Issues {@code POST /introspect} requests to an Authorization Server and caches positive
 * introspection results for a configurable TTL to reduce round-trips. Negative results
 * ({@code active: false} or error responses) are never cached.
 */
@Component
public class TokenIntrospectionClient {

    private static final Logger log = LoggerFactory.getLogger(TokenIntrospectionClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    public static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(60);

    private final WebClient webClient;
    private final Clock clock;
    private final Duration defaultCacheTtl;
    private final ConcurrentHashMap<String, CacheEntry> positiveCache = new ConcurrentHashMap<>();

    @Autowired
    public TokenIntrospectionClient() {
        this(WebClient.builder().build(), Clock.systemUTC(), DEFAULT_CACHE_TTL);
    }

    public TokenIntrospectionClient(WebClient webClient) {
        this(webClient, Clock.systemUTC(), DEFAULT_CACHE_TTL);
    }

    public TokenIntrospectionClient(WebClient webClient, Duration defaultCacheTtl) {
        this(webClient, Clock.systemUTC(), defaultCacheTtl);
    }

    public TokenIntrospectionClient(WebClient webClient, Clock clock) {
        this(webClient, clock, DEFAULT_CACHE_TTL);
    }

    public TokenIntrospectionClient(WebClient webClient, Clock clock, Duration defaultCacheTtl) {
        this.webClient = webClient != null ? webClient : WebClient.builder().build();
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.defaultCacheTtl = defaultCacheTtl != null ? defaultCacheTtl : DEFAULT_CACHE_TTL;
    }

    /**
     * Introspects an opaque or structured token using tenant plugin configuration.
     *
     * @param token  token string presented by the caller
     * @param config plugin configuration map
     * @return Mono emitting decoded token claims if active, or empty Mono if inactive/invalid
     */
    public Mono<Map<String, Object>> introspect(String token, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Mono.empty();
        }
        String endpoint = OAuth2OidcConfigSchema.extractIntrospectionEndpoint(config);
        String clientId = OAuth2OidcConfigSchema.extractClientId(config);
        String clientSecret = OAuth2OidcConfigSchema.extractClientSecret(config);
        long ttlSeconds = OAuth2OidcConfigSchema.extractIntrospectionCacheTtlSeconds(config);
        Duration ttl = Duration.ofSeconds(ttlSeconds);

        return introspect(token, endpoint, clientId, clientSecret, ttl);
    }

    /**
     * Introspects a token against the specified endpoint.
     *
     * @param token                token string to introspect
     * @param introspectionEndpoint AS introspection URL
     * @param clientId             client identifier for AS auth
     * @param clientSecret         client secret for AS auth
     * @param cacheTtl             TTL for positive introspection results
     * @return Mono emitting claims if active, or empty Mono if inactive
     */
    public Mono<Map<String, Object>> introspect(
            String token,
            String introspectionEndpoint,
            String clientId,
            String clientSecret,
            Duration cacheTtl
    ) {
        if (token == null || token.isBlank() || introspectionEndpoint == null || introspectionEndpoint.isBlank()) {
            return Mono.empty();
        }

        String cacheKey = computeCacheKey(token, introspectionEndpoint);
        Instant now = clock.instant();

        // 1. Check positive cache
        CacheEntry cached = positiveCache.get(cacheKey);
        if (cached != null) {
            if (now.isBefore(cached.expiresAt())) {
                return Mono.just(cached.claims());
            } else {
                positiveCache.remove(cacheKey);
            }
        }

        // 2. Prepare RFC 7662 request
        Duration ttl = cacheTtl != null ? cacheTtl : defaultCacheTtl;
        BodyInserters.FormInserter<String> form = BodyInserters.fromFormData("token", token)
                .with("token_type_hint", "access_token");

        if (clientId != null && !clientId.isBlank()) {
            form.with("client_id", clientId);
        }
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.with("client_secret", clientSecret);
        }

        return webClient.post()
                .uri(introspectionEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (clientId != null && clientSecret != null) {
                        headers.setBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8);
                    }
                })
                .body(form)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .flatMap(body -> {
                    Object activeObj = body.get("active");
                    boolean active = Boolean.TRUE.equals(activeObj)
                            || "true".equalsIgnoreCase(String.valueOf(activeObj));

                    if (!active) {
                        log.debug("Token introspection returned active: false");
                        // Negative result: DO NOT cache!
                        return Mono.empty();
                    }

                    // Positive result: compute TTL honoring exp claim if present
                    Duration effectiveTtl = ttl;
                    if (body.containsKey("exp")) {
                        try {
                            long exp = toEpochSeconds(body.get("exp"));
                            long remaining = exp - clock.instant().getEpochSecond();
                            if (remaining > 0 && remaining < effectiveTtl.toSeconds()) {
                                effectiveTtl = Duration.ofSeconds(remaining);
                            }
                        } catch (Exception ignored) {}
                    }

                    Instant expiresAt = clock.instant().plus(effectiveTtl);
                    Map<String, Object> immutableClaims = Collections.unmodifiableMap(new LinkedHashMap<>(body));
                    positiveCache.put(cacheKey, new CacheEntry(immutableClaims, expiresAt));

                    return Mono.just(immutableClaims);
                })
                .onErrorResume(ex -> {
                    log.warn("Token introspection call failed against {}: {}", introspectionEndpoint, ex.getMessage());
                    // Never cache on error
                    return Mono.empty();
                });
    }

    /**
     * Checks if a token is currently present in the positive cache and unexpired.
     */
    public boolean isCached(String token, String endpoint) {
        String key = computeCacheKey(token, endpoint);
        CacheEntry entry = positiveCache.get(key);
        return entry != null && clock.instant().isBefore(entry.expiresAt());
    }

    /**
     * Clears all cached introspection results.
     */
    public void clearCache() {
        positiveCache.clear();
    }

    /**
     * Returns count of currently cached introspection records.
     */
    public int getCacheSize() {
        return positiveCache.size();
    }

    private String computeCacheKey(String token, String endpoint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((token + "@" + endpoint).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            return token + "@" + endpoint;
        }
    }

    private long toEpochSeconds(Object val) {
        if (val instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(val));
    }

    public record CacheEntry(Map<String, Object> claims, Instant expiresAt) {}
}
