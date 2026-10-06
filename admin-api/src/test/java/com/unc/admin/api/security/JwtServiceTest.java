package com.unc.admin.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Phase 23: JwtService Unit Tests")
class JwtServiceTest {

    private static final String SECRET_A = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String SECRET_B = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

    private final Instant baseTime = Instant.parse("2026-10-05T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(SECRET_A, 900, fixedClock);

    @Test
    @DisplayName("JWT issued with configured secret validates successfully and claims match principal")
    void testIssuedJwtValidatesSuccessfully() {
        UUID id = UUID.randomUUID();
        UUID tenantScope = UUID.randomUUID();
        AdminPrincipal principal = new AdminPrincipal(id, "operator@unc.dev", AdminRole.OPERATOR, tenantScope);

        String token = jwtService.issueToken(principal);
        assertThat(token).isNotBlank();

        Optional<AdminPrincipal> verified = jwtService.verifyToken(token);
        assertThat(verified).isPresent();
        AdminPrincipal parsed = verified.get();

        assertThat(parsed.id()).isEqualTo(id);
        assertThat(parsed.email()).isEqualTo("operator@unc.dev");
        assertThat(parsed.role()).isEqualTo(AdminRole.OPERATOR);
        assertThat(parsed.tenantScope()).isEqualTo(tenantScope);
    }

    @Test
    @DisplayName("JWT issued without tenant scope preserves null tenantScope claim")
    void testAdminPrincipalNullTenantScope() {
        UUID id = UUID.randomUUID();
        AdminPrincipal admin = new AdminPrincipal(id, "admin@unc.dev", AdminRole.ADMIN, null);

        String token = jwtService.issueToken(admin);
        Optional<AdminPrincipal> verified = jwtService.verifyToken(token);

        assertThat(verified).isPresent();
        assertThat(verified.get().tenantScope()).isNull();
        assertThat(verified.get().role()).isEqualTo(AdminRole.ADMIN);
    }

    @Test
    @DisplayName("JWT signed with different secret is rejected")
    void testJwtSignedWithDifferentSecretRejected() {
        JwtService otherService = new JwtService(SECRET_B, 900, fixedClock);
        AdminPrincipal principal = new AdminPrincipal(UUID.randomUUID(), "user@unc.dev", AdminRole.VIEWER, UUID.randomUUID());

        String foreignToken = otherService.issueToken(principal);

        Optional<AdminPrincipal> verified = jwtService.verifyToken(foreignToken);
        assertThat(verified).isEmpty();
    }

    @Test
    @DisplayName("Expired JWT (clock advanced beyond expiry) is rejected")
    void testExpiredJwtRejected() {
        AdminPrincipal principal = new AdminPrincipal(UUID.randomUUID(), "user@unc.dev", AdminRole.OPERATOR, UUID.randomUUID());
        String token = jwtService.issueToken(principal);

        // Advance clock by 901 seconds (past 900s expiry)
        Clock advancedClock = Clock.fixed(baseTime.plusSeconds(901), ZoneOffset.UTC);
        JwtService verifierWithAdvancedClock = new JwtService(SECRET_A, 900, advancedClock);

        Optional<AdminPrincipal> verified = verifierWithAdvancedClock.verifyToken(token);
        assertThat(verified).isEmpty();
    }

    @Test
    @DisplayName("Invalid or malformed JWT string is rejected")
    void testMalformedJwtRejected() {
        assertThat(jwtService.verifyToken("invalid.token")).isEmpty();
        assertThat(jwtService.verifyToken("")).isEmpty();
        assertThat(jwtService.verifyToken(null)).isEmpty();
    }

    @Test
    @DisplayName("Short secret (<256 bits) throws IllegalArgumentException on init")
    void testShortSecretRejected() {
        assertThatThrownBy(() -> new JwtService("short-secret", 900, fixedClock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
