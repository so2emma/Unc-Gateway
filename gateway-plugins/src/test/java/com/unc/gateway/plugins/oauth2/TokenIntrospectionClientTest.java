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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TokenIntrospectionClientTest {

    private static final String INTROSPECT_URL = "https://as.example.com/oauth/introspect";

    @Test
    @DisplayName("TokenIntrospectionClient: active: true response is accepted and scope claim forwarded")
    void testActiveTrueAccepted() {
        AtomicInteger callCount = new AtomicInteger(0);

        ExchangeFunction exchangeFunction = req -> {
            callCount.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {
                              "active": true,
                              "sub": "user-introspect-1",
                              "scope": "api:read api:write",
                              "client_id": "gateway-core-client"
                            }
                            """)
                    .build());
        };

        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        TokenIntrospectionClient client = new TokenIntrospectionClient(webClient);

        Map<String, Object> config = Map.of(
                "introspection_endpoint", INTROSPECT_URL,
                "client_id", "gateway-core-client",
                "client_secret", "secret"
        );

        StepVerifier.create(client.introspect("opaque-token-123", config))
                .assertNext(claims -> {
                    assertThat(claims).containsEntry("active", true);
                    assertThat(claims).containsEntry("sub", "user-introspect-1");
                    assertThat(claims).containsEntry("scope", "api:read api:write");
                })
                .verifyComplete();

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("TokenIntrospectionClient: active: false response returns empty Mono (causes 401)")
    void testActiveFalseReturnsEmpty() {
        AtomicInteger callCount = new AtomicInteger(0);

        ExchangeFunction exchangeFunction = req -> {
            callCount.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"active\": false}")
                    .build());
        };

        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        TokenIntrospectionClient client = new TokenIntrospectionClient(webClient);

        Map<String, Object> config = Map.of(
                "introspection_endpoint", INTROSPECT_URL,
                "client_id", "gateway-core-client",
                "client_secret", "secret"
        );

        StepVerifier.create(client.introspect("revoked-token", config))
                .verifyComplete();

        assertThat(callCount.get()).isEqualTo(1);
        // Negative result must NOT be cached
        assertThat(client.getCacheSize()).isZero();
    }

    @Test
    @DisplayName("TokenIntrospectionClient: cached positive result within TTL does not issue second AS call")
    void testPositiveResultCachedWithinTtl() {
        AtomicInteger callCount = new AtomicInteger(0);

        ExchangeFunction exchangeFunction = req -> {
            callCount.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"active\": true, \"sub\": \"cached-user\"}")
                    .build());
        };

        Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        TokenIntrospectionClient client = new TokenIntrospectionClient(webClient, clock, Duration.ofSeconds(60));

        Map<String, Object> config = Map.of(
                "introspection_endpoint", INTROSPECT_URL,
                "client_id", "gateway-core-client",
                "client_secret", "secret"
        );

        // 1. First call -> calls AS
        Map<String, Object> claims1 = client.introspect("token-cached", config).block();
        assertThat(claims1).containsEntry("sub", "cached-user");
        assertThat(callCount.get()).isEqualTo(1);

        // 2. Second call within TTL -> returns from cache without calling AS
        Map<String, Object> claims2 = client.introspect("token-cached", config).block();
        assertThat(claims2).containsEntry("sub", "cached-user");
        assertThat(callCount.get()).isEqualTo(1); // Still 1!
    }
}
