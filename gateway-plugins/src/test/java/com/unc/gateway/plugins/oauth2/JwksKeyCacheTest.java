package com.unc.gateway.plugins.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwksKeyCacheTest {

    private static final String JWKS_URI = "https://as.example.com/oauth/keys";

    static class TestClock extends Clock {
        private Instant now;

        TestClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
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

    private static String toJwksJson(String kid, PublicKey publicKey) {
        RSAPublicKey rsa = (RSAPublicKey) publicKey;
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(rsa.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(rsa.getPublicExponent().toByteArray());
        return """
                {
                  "keys": [
                    {
                      "kty": "RSA",
                      "kid": "%s",
                      "n": "%s",
                      "e": "%s"
                    }
                  ]
                }
                """.formatted(kid, n, e);
    }

    @Test
    @DisplayName("JwksKeyCache: after configured refresh interval elapses, cache issues a new JWKS fetch")
    void testRefreshAfterIntervalElapses() {
        KeyPair keyPair1 = generateRsaKeyPair();
        KeyPair keyPair2 = generateRsaKeyPair();

        AtomicInteger callCount = new AtomicInteger(0);
        AtomicReference<String> responseJson = new AtomicReference<>(toJwksJson("kid-1", keyPair1.getPublic()));

        ExchangeFunction exchangeFunction = req -> {
            callCount.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(responseJson.get())
                    .build());
        };

        TestClock clock = new TestClock(Instant.parse("2026-01-01T00:00:00Z"));
        Duration refreshInterval = Duration.ofSeconds(60);
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        JwksKeyCache cache = new JwksKeyCache(webClient, clock, refreshInterval);

        // 1. First fetch (cold start)
        StepVerifier.create(cache.getKey(JWKS_URI, "kid-1", refreshInterval))
                .assertNext(pk -> assertThat(pk).isEqualTo(keyPair1.getPublic()))
                .verifyComplete();
        assertThat(callCount.get()).isEqualTo(1);

        // 2. Immediate second call within refresh interval -> should use cache without new fetch
        clock.advance(Duration.ofSeconds(10));
        StepVerifier.create(cache.getKey(JWKS_URI, "kid-1", refreshInterval))
                .assertNext(pk -> assertThat(pk).isEqualTo(keyPair1.getPublic()))
                .verifyComplete();
        assertThat(callCount.get()).isEqualTo(1);

        // 3. Advance past refresh interval and rotate key
        clock.advance(Duration.ofSeconds(60));
        responseJson.set(toJwksJson("kid-2", keyPair2.getPublic()));

        // Call again -> triggers stale-while-revalidate background refresh and serves new key
        cache.getKey(JWKS_URI, "kid-2", refreshInterval).block(Duration.ofSeconds(2));
        assertThat(callCount.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("JwksKeyCache: concurrent request during refresh is served stale key set (stale-while-revalidate)")
    void testStaleWhileRevalidateServesExistingKey() throws Exception {
        KeyPair initialKey = generateRsaKeyPair();
        KeyPair rotatedKey = generateRsaKeyPair();

        TestClock clock = new TestClock(Instant.parse("2026-01-01T00:00:00Z"));
        Duration refreshInterval = Duration.ofSeconds(60);

        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch allowRefreshToComplete = new CountDownLatch(1);

        AtomicReference<String> currentJwks = new AtomicReference<>(toJwksJson("kid-active", initialKey.getPublic()));

        ExchangeFunction exchangeFunction = req -> {
            if (clock.instant().isAfter(Instant.parse("2026-01-01T00:01:00Z"))) {
                refreshStarted.countDown();
                try {
                    allowRefreshToComplete.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(currentJwks.get())
                    .build());
        };

        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        JwksKeyCache cache = new JwksKeyCache(webClient, clock, refreshInterval);

        // Initial fetch
        PublicKey pk1 = cache.getKey(JWKS_URI, "kid-active", refreshInterval).block();
        assertThat(pk1).isEqualTo(initialKey.getPublic());

        // Elapse TTL
        clock.advance(Duration.ofSeconds(70));
        currentJwks.set(toJwksJson("kid-active", rotatedKey.getPublic()));

        // Non-blocking request during expired state immediately gets stale key without waiting for refresh!
        PublicKey stalePk = cache.getKey(JWKS_URI, "kid-active", refreshInterval).block(Duration.ofMillis(500));
        assertThat(stalePk).isEqualTo(initialKey.getPublic());

        // Allow background refresh to finish
        allowRefreshToComplete.countDown();
    }

    @Test
    @DisplayName("JwksKeyCache: HTTP error from JWKS endpoint leaves existing cache entries intact")
    void testHttpErrorLeavesCacheIntact() {
        KeyPair keyPair = generateRsaKeyPair();
        AtomicInteger callCount = new AtomicInteger(0);

        ExchangeFunction exchangeFunction = req -> {
            if (callCount.incrementAndGet() == 1) {
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(toJwksJson("kid-1", keyPair.getPublic()))
                        .build());
            } else {
                return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Server Error")
                        .build());
            }
        };

        TestClock clock = new TestClock(Instant.parse("2026-01-01T00:00:00Z"));
        Duration refreshInterval = Duration.ofSeconds(60);
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        JwksKeyCache cache = new JwksKeyCache(webClient, clock, refreshInterval);

        // 1. Initial success
        PublicKey pk1 = cache.getKey(JWKS_URI, "kid-1", refreshInterval).block();
        assertThat(pk1).isEqualTo(keyPair.getPublic());

        // 2. Elapse refresh interval -> next call triggers refresh which returns 500
        clock.advance(Duration.ofSeconds(70));
        PublicKey pk2 = cache.getKey(JWKS_URI, "kid-1", refreshInterval).block();

        // 3. Must still serve the intact cached key!
        assertThat(pk2).isEqualTo(keyPair.getPublic());
        assertThat(cache.getCachedKeys(JWKS_URI)).containsKey("kid-1");
    }
}
