package com.unc.gateway.plugins.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe, reactive cache of JWKS {@code kid -> PublicKey} entries.
 * <p>
 * Fetches the Authorization Server's JWKS endpoint on startup and on key-not-found cache miss.
 * Implements the stale-while-revalidate pattern so that request threads are never blocked during
 * scheduled JWKS key rotations, and leaves existing cache entries intact if an upstream JWKS fetch fails.
 */
@Component
public class JwksKeyCache {

    private static final Logger log = LoggerFactory.getLogger(JwksKeyCache.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofSeconds(300);

    private final WebClient webClient;
    private final Clock clock;
    private final Duration defaultRefreshInterval;
    private final ConcurrentHashMap<String, UriState> uriStates = new ConcurrentHashMap<>();

    @Autowired
    public JwksKeyCache() {
        this(WebClient.builder().build(), Clock.systemUTC(), DEFAULT_REFRESH_INTERVAL);
    }

    public JwksKeyCache(WebClient webClient) {
        this(webClient, Clock.systemUTC(), DEFAULT_REFRESH_INTERVAL);
    }

    public JwksKeyCache(WebClient webClient, Duration defaultRefreshInterval) {
        this(webClient, Clock.systemUTC(), defaultRefreshInterval);
    }

    public JwksKeyCache(WebClient webClient, Clock clock) {
        this(webClient, clock, DEFAULT_REFRESH_INTERVAL);
    }

    public JwksKeyCache(WebClient webClient, Clock clock, Duration defaultRefreshInterval) {
        this.webClient = webClient != null ? webClient : WebClient.builder().build();
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.defaultRefreshInterval = defaultRefreshInterval != null ? defaultRefreshInterval : DEFAULT_REFRESH_INTERVAL;
    }

    /**
     * Resolves a public key by key ID (kid) from the specified JWKS endpoint.
     *
     * @param jwksUri JWKS endpoint URI
     * @param kid     key ID (can be null/blank if the JWKS has a single key)
     * @return Mono emitting the resolved {@link PublicKey}, or empty Mono if not found
     */
    public Mono<PublicKey> getKey(String jwksUri, String kid) {
        return getKey(jwksUri, kid, this.defaultRefreshInterval);
    }

    /**
     * Resolves a public key by key ID (kid) with a custom cache refresh interval.
     *
     * @param jwksUri         JWKS endpoint URI
     * @param kid             key ID
     * @param refreshInterval cache TTL before a background revalidation is triggered
     * @return Mono emitting the resolved {@link PublicKey}, or empty Mono if not found
     */
    public Mono<PublicKey> getKey(String jwksUri, String kid, Duration refreshInterval) {
        if (jwksUri == null || jwksUri.isBlank()) {
            return Mono.empty();
        }

        Duration interval = refreshInterval != null ? refreshInterval : defaultRefreshInterval;
        UriState state = uriStates.computeIfAbsent(jwksUri, u -> new UriState());
        CacheEntry entry = state.currentEntry.get();
        Instant now = clock.instant();

        if (entry == null) {
            // Cold start: fetch synchronously before returning
            return fetchKeys(jwksUri, state, interval, false)
                    .flatMap(keys -> Mono.justOrEmpty(findKey(keys, kid)));
        }

        boolean isExpired = entry.isExpired(now);
        if (isExpired) {
            // Stale-while-revalidate: trigger background refresh asynchronously without blocking callers
            triggerBackgroundRefresh(jwksUri, state, interval);

            // Can only serve stale if within maximum stale window (1 refresh interval past expiry)
            boolean withinStaleWindow = !now.isAfter(entry.getExpiresAt().plus(interval));
            PublicKey staleKey = withinStaleWindow ? findKey(entry.getKeys(), kid) : null;
            if (staleKey != null) {
                return Mono.just(staleKey);
            }

            // Stale window exceeded or key not in stale entry: wait for in-flight/new refresh to complete
            return fetchKeys(jwksUri, state, interval, false)
                    .flatMap(keys -> Mono.justOrEmpty(findKey(keys, kid)));
        }

        // Cache is fresh
        PublicKey freshKey = findKey(entry.getKeys(), kid);
        if (freshKey != null) {
            return Mono.just(freshKey);
        }

        // Key-not-found cache miss: force refresh immediately before giving up
        return forceRefresh(jwksUri, interval)
                .flatMap(keys -> Mono.justOrEmpty(findKey(keys, kid)));
    }

    /**
     * Forces an immediate JWKS fetch from the Authorization Server, updating cache entries on success.
     *
     * @param jwksUri JWKS endpoint URI
     * @return Mono emitting all cached public keys mapped by kid
     */
    public Mono<Map<String, PublicKey>> forceRefresh(String jwksUri) {
        return forceRefresh(jwksUri, this.defaultRefreshInterval);
    }

    /**
     * Forces an immediate JWKS fetch with custom refresh interval.
     */
    public Mono<Map<String, PublicKey>> forceRefresh(String jwksUri, Duration refreshInterval) {
        UriState state = uriStates.computeIfAbsent(jwksUri, u -> new UriState());
        Duration interval = refreshInterval != null ? refreshInterval : defaultRefreshInterval;

        return webClient.get()
                .uri(jwksUri)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseJwks)
                .doOnNext(newKeys -> {
                    Instant now = clock.instant();
                    state.currentEntry.set(new CacheEntry(newKeys, now, now.plus(interval)));
                })
                .onErrorResume(ex -> {
                    log.warn("Force JWKS refresh failed for {}: {}", jwksUri, ex.getMessage());
                    CacheEntry existing = state.currentEntry.get();
                    if (existing != null) {
                        return Mono.just(existing.getKeys());
                    }
                    return Mono.error(ex);
                });
    }

    /**
     * Retrieves currently cached keys without triggering any network call.
     */
    public Map<String, PublicKey> getCachedKeys(String jwksUri) {
        UriState state = uriStates.get(jwksUri);
        if (state == null) {
            return Collections.emptyMap();
        }
        CacheEntry entry = state.currentEntry.get();
        return entry != null ? entry.getKeys() : Collections.emptyMap();
    }

    /**
     * Clears all cache entries across all URIs.
     */
    public void clear() {
        uriStates.clear();
    }

    private Mono<Map<String, PublicKey>> fetchKeys(String jwksUri, UriState state, Duration interval, boolean force) {
        if (!force) {
            Mono<Map<String, PublicKey>> inFlight = state.inFlight.get();
            if (inFlight != null) {
                return inFlight;
            }
        }

        Mono<Map<String, PublicKey>> call = webClient.get()
                .uri(jwksUri)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseJwks)
                .doOnNext(newKeys -> {
                    Instant now = clock.instant();
                    state.currentEntry.set(new CacheEntry(newKeys, now, now.plus(interval)));
                })
                .doFinally(signalType -> state.inFlight.set(null))
                .share();

        if (state.inFlight.compareAndSet(null, call)) {
            return call;
        } else {
            Mono<Map<String, PublicKey>> existing = state.inFlight.get();
            return existing != null ? existing : call;
        }
    }

    private void triggerBackgroundRefresh(String jwksUri, UriState state, Duration interval) {
        fetchKeys(jwksUri, state, interval, false)
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("Background JWKS refresh failed for {}: {}. Serving stale keys.", jwksUri, ex.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    private PublicKey findKey(Map<String, PublicKey> keys, String kid) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        if (kid != null && !kid.isBlank()) {
            return keys.get(kid);
        }
        if (keys.containsKey("")) {
            return keys.get("");
        }
        if (keys.size() == 1) {
            return keys.values().iterator().next();
        }
        return null;
    }

    public Map<String, PublicKey> parseJwks(String json) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode keysNode = root.get("keys");
            Map<String, PublicKey> keyMap = new HashMap<>();

            if (keysNode != null && keysNode.isArray()) {
                for (JsonNode k : keysNode) {
                    String kid = k.has("kid") ? k.get("kid").asText() : null;
                    try {
                        PublicKey pk = parseSingleJwk(k);
                        if (pk != null) {
                            if (kid != null && !kid.isBlank()) {
                                keyMap.put(kid, pk);
                            }
                            keyMap.putIfAbsent("", pk);
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to parse JWK entry [kid={}]: {}", kid, ex.getMessage());
                    }
                }
            }

            return Collections.unmodifiableMap(keyMap);
        } catch (Exception e) {
            log.error("Failed to parse JWKS JSON response: {}", e.getMessage());
            throw new RuntimeException("Malformed JWKS response", e);
        }
    }

    private PublicKey parseSingleJwk(JsonNode k) throws Exception {
        String kty = k.has("kty") ? k.get("kty").asText() : "";
        if ("RSA".equalsIgnoreCase(kty)) {
            if (!k.has("n") || !k.has("e")) {
                throw new IllegalArgumentException("RSA JWK missing 'n' or 'e'");
            }
            String nStr = k.get("n").asText();
            String eStr = k.get("e").asText();
            byte[] nBytes = Base64.getUrlDecoder().decode(nStr);
            byte[] eBytes = Base64.getUrlDecoder().decode(eStr);
            BigInteger modulus = new BigInteger(1, nBytes);
            BigInteger exponent = new BigInteger(1, eBytes);
            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } else if ("EC".equalsIgnoreCase(kty)) {
            if (!k.has("crv") || !k.has("x") || !k.has("y")) {
                throw new IllegalArgumentException("EC JWK missing 'crv', 'x', or 'y'");
            }
            String crv = k.get("crv").asText();
            String xStr = k.get("x").asText();
            String yStr = k.get("y").asText();
            byte[] xBytes = Base64.getUrlDecoder().decode(xStr);
            byte[] yBytes = Base64.getUrlDecoder().decode(yStr);
            BigInteger x = new BigInteger(1, xBytes);
            BigInteger y = new BigInteger(1, yBytes);
            ECPoint point = new ECPoint(x, y);
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            String stdCurve = switch (crv.toUpperCase(Locale.ROOT)) {
                case "P-256", "SECP256R1" -> "secp256r1";
                case "P-384", "SECP384R1" -> "secp384r1";
                case "P-521", "SECP521R1" -> "secp521r1";
                default -> crv;
            };
            params.init(new ECGenParameterSpec(stdCurve));
            ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);
            ECPublicKeySpec spec = new ECPublicKeySpec(point, ecSpec);
            return KeyFactory.getInstance("EC").generatePublic(spec);
        } else if (k.has("x5c") && k.get("x5c").isArray() && k.get("x5c").size() > 0) {
            byte[] certBytes = Base64.getDecoder().decode(k.get("x5c").get(0).asText());
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(new ByteArrayInputStream(certBytes));
            return cert.getPublicKey();
        }
        throw new IllegalArgumentException("Unsupported JWK key type: " + kty);
    }

    public static class CacheEntry {
        private final Map<String, PublicKey> keys;
        private final Instant fetchedAt;
        private final Instant expiresAt;

        public CacheEntry(Map<String, PublicKey> keys, Instant fetchedAt, Instant expiresAt) {
            this.keys = keys != null ? keys : Collections.emptyMap();
            this.fetchedAt = fetchedAt;
            this.expiresAt = expiresAt;
        }

        public Map<String, PublicKey> getKeys() {
            return keys;
        }

        public Instant getFetchedAt() {
            return fetchedAt;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    private static class UriState {
        final AtomicReference<CacheEntry> currentEntry = new AtomicReference<>(null);
        final AtomicReference<Mono<Map<String, PublicKey>>> inFlight = new AtomicReference<>(null);
    }
}
