package com.unc.gateway.plugins;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtVerifierTest {

    private final Instant fixedNow = Instant.parse("2026-10-04T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    private final JwtVerifier verifier = new JwtVerifier(fixedClock);

    private final String secret = "super-secret-key-that-is-secure-12345";

    @Test
    @DisplayName("verify: token signed with configured secret and future exp verifies successfully")
    void testValidTokenVerifiesSuccessfully() {
        long futureExp = fixedNow.getEpochSecond() + 3600; // +1 hr
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "role", "admin",
                "exp", futureExp
        );

        String token = JwtVerifier.createHmacToken(claims, secret, "HS256");

        Optional<Map<String, Object>> result = verifier.verify(token, secret, "HS256");

        assertThat(result).isPresent();
        Map<String, Object> decoded = result.get();
        assertThat(decoded.get("sub")).isEqualTo("user-123");
        assertThat(decoded.get("role")).isEqualTo("admin");
        assertThat(((Number) decoded.get("exp")).longValue()).isEqualTo(futureExp);
    }

    @Test
    @DisplayName("verify: works with short secret like demo-secret")
    void testShortSecretVerification() {
        String shortSecret = "demo-secret";
        long futureExp = fixedNow.getEpochSecond() + 600;
        Map<String, Object> claims = Map.of("sub", "demo-user", "exp", futureExp);

        String token = JwtVerifier.createHmacToken(claims, shortSecret, "HS256");

        Optional<Map<String, Object>> result = verifier.verify(token, shortSecret, "HS256");
        assertThat(result).isPresent();
        assertThat(result.get().get("sub")).isEqualTo("demo-user");
    }

    @Test
    @DisplayName("verify: token signed with different secret fails verification")
    void testWrongSecretFailsVerification() {
        long futureExp = fixedNow.getEpochSecond() + 3600;
        Map<String, Object> claims = Map.of("sub", "user-123", "exp", futureExp);

        String token = JwtVerifier.createHmacToken(claims, secret, "HS256");

        Optional<Map<String, Object>> result = verifier.verify(token, "wrong-secret-key-99999999", "HS256");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("verify: invalid or truncated token structure fails verification")
    void testInvalidOrTruncatedStructureFails() {
        assertThat(verifier.verify(null, secret, "HS256")).isEmpty();
        assertThat(verifier.verify("", secret, "HS256")).isEmpty();
        assertThat(verifier.verify("not-a-jwt", secret, "HS256")).isEmpty();
        assertThat(verifier.verify("part1.part2", secret, "HS256")).isEmpty();
        assertThat(verifier.verify("part1.part2.part3.part4", secret, "HS256")).isEmpty();

        // Truncated signature
        long futureExp = fixedNow.getEpochSecond() + 3600;
        String token = JwtVerifier.createHmacToken(Map.of("sub", "test", "exp", futureExp), secret, "HS256");
        String truncatedToken = token.substring(0, token.length() - 5);
        assertThat(verifier.verify(truncatedToken, secret, "HS256")).isEmpty();

        // Non-base64 garbage
        assertThat(verifier.verify("???.!!!.***", secret, "HS256")).isEmpty();
    }

    @Test
    @DisplayName("verify: token with past exp claim fails verification")
    void testPastExpFailsVerification() {
        long pastExp = fixedNow.getEpochSecond() - 60; // 1 min ago
        Map<String, Object> claims = Map.of(
                "sub", "user-expired",
                "exp", pastExp
        );

        String token = JwtVerifier.createHmacToken(claims, secret, "HS256");

        Optional<Map<String, Object>> result = verifier.verify(token, secret, "HS256");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("verify: token with future nbf claim fails verification")
    void testFutureNbfFailsVerification() {
        long futureNbf = fixedNow.getEpochSecond() + 300; // not valid for 5 more minutes
        long futureExp = fixedNow.getEpochSecond() + 3600;
        Map<String, Object> claims = Map.of(
                "sub", "user-early",
                "nbf", futureNbf,
                "exp", futureExp
        );

        String token = JwtVerifier.createHmacToken(claims, secret, "HS256");

        Optional<Map<String, Object>> result = verifier.verify(token, secret, "HS256");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("verify: algorithm mismatch fails verification")
    void testAlgorithmMismatchFails() {
        long futureExp = fixedNow.getEpochSecond() + 3600;
        String token = JwtVerifier.createHmacToken(Map.of("sub", "user", "exp", futureExp), secret, "HS384");

        // Configured for HS256, but token is HS384
        Optional<Map<String, Object>> result = verifier.verify(token, secret, "HS256");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("verify: none algorithm is strictly rejected")
    void testNoneAlgorithmRejected() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"hacker\"}".getBytes());
        String token = header + "." + payload + ".";

        assertThat(verifier.verify(token, secret, "HS256")).isEmpty();
        assertThat(verifier.verify(token, secret, "none")).isEmpty();
    }

    @Test
    @DisplayName("verify: supports HS384 and HS512 algorithms")
    void testHs384AndHs512() {
        long futureExp = fixedNow.getEpochSecond() + 3600;

        String token384 = JwtVerifier.createHmacToken(Map.of("sub", "user-384", "exp", futureExp), secret, "HS384");
        Optional<Map<String, Object>> res384 = verifier.verify(token384, secret, "HS384");
        assertThat(res384).isPresent();
        assertThat(res384.get().get("sub")).isEqualTo("user-384");

        String token512 = JwtVerifier.createHmacToken(Map.of("sub", "user-512", "exp", futureExp), secret, "HS512");
        Optional<Map<String, Object>> res512 = verifier.verify(token512, secret, "HS512");
        assertThat(res512).isPresent();
        assertThat(res512.get().get("sub")).isEqualTo("user-512");
    }

    @Test
    @DisplayName("verify: verifies via config map")
    void testVerifyViaConfigMap() {
        long futureExp = fixedNow.getEpochSecond() + 3600;
        String token = JwtVerifier.createHmacToken(Map.of("sub", "config-user", "exp", futureExp), secret, "HS256");

        Map<String, Object> config = Map.of(
                "secret", secret,
                "algorithm", "HS256"
        );

        Optional<Map<String, Object>> result = verifier.verify(token, config);
        assertThat(result).isPresent();
        assertThat(result.get().get("sub")).isEqualTo("config-user");
    }
}
