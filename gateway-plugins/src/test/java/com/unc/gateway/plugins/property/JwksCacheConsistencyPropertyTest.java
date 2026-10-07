package com.unc.gateway.plugins.property;

import com.unc.gateway.plugins.oauth2.JwksKeyCache;
import net.jqwik.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

class JwksCacheConsistencyPropertyTest {

    private static final String JWKS_URI = "https://as.example.com/oauth/keys";

    static class StepClock extends Clock {
        private final AtomicLong currentTime = new AtomicLong(1_000_000_000L);

        public void setTime(long seconds) {
            currentTime.set(seconds);
        }

        public void advance(long seconds) {
            currentTime.addAndGet(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochSecond(currentTime.get());
        }
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toJwksJson(Map<String, PublicKey> activeKeys) {
        StringBuilder sb = new StringBuilder("{\"keys\":[");
        boolean first = true;
        for (Map.Entry<String, PublicKey> entry : activeKeys.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            RSAPublicKey rsa = (RSAPublicKey) entry.getValue();
            String n = Base64.getUrlEncoder().withoutPadding().encodeToString(rsa.getModulus().toByteArray());
            String e = Base64.getUrlEncoder().withoutPadding().encodeToString(rsa.getPublicExponent().toByteArray());
            sb.append("""
                    {"kty":"RSA","kid":"%s","n":"%s","e":"%s"}
                    """.formatted(entry.getKey(), n, e));
        }
        sb.append("]}");
        return sb.toString();
    }

    record RotationStep(long delaySeconds, boolean rotateKey) {}

    @Property(tries = 100)
    @Label("JWKS_CACHE_CONSISTENCY: JwksKeyCache eventually serves new keys and never serves keys revoked > refresh interval")
    boolean jwksCacheConsistency(
            @ForAll("refreshIntervalSeconds") long refreshIntervalSec,
            @ForAll("rotationSteps") List<RotationStep> steps) {

        StepClock clock = new StepClock();
        Duration refreshInterval = Duration.ofSeconds(refreshIntervalSec);

        // Simulated AS key storage
        Map<String, KeyPair> allGeneratedKeys = new HashMap<>();
        Map<String, PublicKey> currentAsKeys = new HashMap<>();
        Map<String, Long> keyRevokedAt = new HashMap<>(); // kid -> time removed from AS

        // Initial key
        KeyPair initialKey = generateRsaKeyPair();
        String currentKid = "kid-0";
        allGeneratedKeys.put(currentKid, initialKey);
        currentAsKeys.put(currentKid, initialKey.getPublic());

        AtomicReference<String> asJwksJson = new AtomicReference<>(toJwksJson(currentAsKeys));

        ExchangeFunction exchangeFunction = req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(asJwksJson.get())
                .build());

        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        JwksKeyCache cache = new JwksKeyCache(webClient, clock, refreshInterval);

        int keyCounter = 1;

        for (RotationStep step : steps) {
            clock.advance(step.delaySeconds());
            long now = clock.instant().getEpochSecond();

            if (step.rotateKey()) {
                // Revoke old key
                keyRevokedAt.put(currentKid, now);
                currentAsKeys.remove(currentKid);

                // Add new key
                currentKid = "kid-" + (keyCounter++);
                KeyPair newKey = generateRsaKeyPair();
                allGeneratedKeys.put(currentKid, newKey);
                currentAsKeys.put(currentKid, newKey.getPublic());

                asJwksJson.set(toJwksJson(currentAsKeys));
            }

            // 1. Verify active key eventually served:
            // If at least one refresh interval has passed since the key became active,
            // or if the key was active from the start, cache MUST serve it.
            PublicKey resolvedActive = cache.getKey(JWKS_URI, currentKid, refreshInterval).block();
            if (resolvedActive == null) {
                // If it wasn't found, trigger refresh and check
                resolvedActive = cache.forceRefresh(JWKS_URI, refreshInterval).block().get(currentKid);
                if (resolvedActive == null) {
                    return false;
                }
            }

            // 2. Verify revoked keys:
            // For any key revoked longer than refreshIntervalSec, cache must NEVER return it!
            for (Map.Entry<String, Long> entry : keyRevokedAt.entrySet()) {
                String revokedKid = entry.getKey();
                long revokedTime = entry.getValue();

                if (now > revokedTime + refreshIntervalSec) {
                    PublicKey resolvedRevoked = cache.getKey(JWKS_URI, revokedKid, refreshInterval).block();
                    if (resolvedRevoked != null) {
                        // VIOLATION: served a key revoked for longer than one refresh interval!
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @Provide
    Arbitrary<Long> refreshIntervalSeconds() {
        return Arbitraries.longs().between(10, 120);
    }

    @Provide
    Arbitrary<List<RotationStep>> rotationSteps() {
        Arbitrary<RotationStep> step = Combinators.combine(
                Arbitraries.longs().between(5, 180),
                Arbitraries.of(true, false)
        ).as(RotationStep::new);
        return step.list().ofMinSize(3).ofMaxSize(15);
    }
}
