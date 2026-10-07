package com.unc.gateway.plugins.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JwksJwtValidatorTest {

    private static final String JWKS_URI = "https://keycloak.local/realms/unc-dev/protocol/openid-connect/certs";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toJwksJson(String kid, RSAPublicKey rsa) {
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

    private static String mintRsaToken(String kid, Map<String, Object> claims, PrivateKey privateKey) {
        try {
            String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}").getBytes(StandardCharsets.UTF_8));
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    OBJECT_MAPPER.writeValueAsBytes(claims));
            String signingInput = header + "." + payload;

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = sig.sign();
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("JwksJwtValidator: JWT signed with test RSA private key passes validation")
    void testValidRsaSignedJwtPasses() {
        KeyPair keyPair = generateRsaKeyPair();
        String kid = "key-test-1";

        ExchangeFunction exchangeFunction = req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(toJwksJson(kid, (RSAPublicKey) keyPair.getPublic()))
                .build());

        Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        JwksKeyCache cache = new JwksKeyCache(webClient, clock);
        JwksJwtValidator validator = new JwksJwtValidator(cache, clock);

        Map<String, Object> claims = Map.of(
                "sub", "user-42",
                "iss", "https://keycloak.local/realms/unc-dev",
                "aud", "gateway-core",
                "exp", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond()
        );
        String token = mintRsaToken(kid, claims, keyPair.getPrivate());

        Map<String, Object> config = Map.of(
                "jwks_uri", JWKS_URI,
                "issuer", "https://keycloak.local/realms/unc-dev",
                "audience", "gateway-core"
        );

        StepVerifier.create(validator.validate(token, config))
                .assertNext(decoded -> {
                    assertThat(decoded).containsEntry("sub", "user-42");
                    assertThat(decoded).containsEntry("iss", "https://keycloak.local/realms/unc-dev");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("JwksJwtValidator: unknown kid triggers JWKS cache refresh and fails if kid is still not found")
    void testUnknownKidTriggersRefreshAndFailsIfNotFound() {
        KeyPair keyPair = generateRsaKeyPair();
        AtomicInteger fetchCount = new AtomicInteger(0);

        ExchangeFunction exchangeFunction = req -> {
            fetchCount.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(toJwksJson("known-kid", (RSAPublicKey) keyPair.getPublic()))
                    .build());
        };

        Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        JwksKeyCache cache = new JwksKeyCache(webClient, clock);
        JwksJwtValidator validator = new JwksJwtValidator(cache, clock);

        Map<String, Object> claims = Map.of(
                "sub", "user-unknown-kid",
                "exp", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond()
        );
        String tokenWithUnknownKid = mintRsaToken("unknown-kid", claims, keyPair.getPrivate());

        Map<String, Object> config = Map.of("jwks_uri", JWKS_URI);

        StepVerifier.create(validator.validate(tokenWithUnknownKid, config))
                .verifyComplete();

        // Must have attempted to fetch/refresh
        assertThat(fetchCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("JwksJwtValidator: JWT with expired exp claim is rejected")
    void testExpiredTokenRejected() {
        KeyPair keyPair = generateRsaKeyPair();
        String kid = "key-exp-1";

        ExchangeFunction exchangeFunction = req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(toJwksJson(kid, (RSAPublicKey) keyPair.getPublic()))
                .build());

        Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        JwksKeyCache cache = new JwksKeyCache(webClient, clock);
        JwksJwtValidator validator = new JwksJwtValidator(cache, clock);

        // exp was 1 hour before current clock time
        Map<String, Object> claims = Map.of(
                "sub", "user-expired",
                "exp", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond()
        );
        String token = mintRsaToken(kid, claims, keyPair.getPrivate());

        Map<String, Object> config = Map.of("jwks_uri", JWKS_URI);

        StepVerifier.create(validator.validate(token, config))
                .verifyComplete(); // Rejected -> empty Mono
    }

    @Test
    @DisplayName("JwksJwtValidator: JWT with mismatched iss claim is rejected")
    void testMismatchedIssuerRejected() {
        KeyPair keyPair = generateRsaKeyPair();
        String kid = "key-iss-1";

        ExchangeFunction exchangeFunction = req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(toJwksJson(kid, (RSAPublicKey) keyPair.getPublic()))
                .build());

        Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        JwksKeyCache cache = new JwksKeyCache(webClient, clock);
        JwksJwtValidator validator = new JwksJwtValidator(cache, clock);

        Map<String, Object> claims = Map.of(
                "sub", "user-bad-iss",
                "iss", "https://malicious-as.com/realm",
                "exp", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond()
        );
        String token = mintRsaToken(kid, claims, keyPair.getPrivate());

        Map<String, Object> config = Map.of(
                "jwks_uri", JWKS_URI,
                "issuer", "https://keycloak.local/realms/unc-dev"
        );

        StepVerifier.create(validator.validate(token, config))
                .verifyComplete(); // Rejected -> empty Mono
    }
}
