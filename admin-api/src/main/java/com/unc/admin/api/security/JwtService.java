package com.unc.admin.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirySeconds;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtService(
            @Value("${admin.jwt.secret:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef}") String secret,
            @Value("${admin.jwt.expiry-seconds:900}") long expirySeconds) {
        this(secret, expirySeconds, Clock.systemUTC());
    }

    public JwtService(String secret, long expirySeconds, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 bytes)");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirySeconds = expirySeconds;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public String issueToken(AdminPrincipal principal) {
        Instant now = clock.instant();
        Instant exp = now.plusSeconds(expirySeconds);

        var builder = Jwts.builder()
                .subject(principal.id().toString())
                .claim("email", principal.email())
                .claim("role", principal.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey);

        if (principal.tenantScope() != null) {
            builder.claim("tenant_scope", principal.tenantScope().toString());
        }

        return builder.compact();
    }

    public Optional<AdminPrincipal> verifyToken(String token) {
        return verifyToken(token, this.signingKey, this.clock);
    }

    public Optional<AdminPrincipal> verifyToken(String token, SecretKey key, Clock clockToUse) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .clock(() -> Date.from(clockToUse.instant()))
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();

            UUID id = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);
            String roleStr = claims.get("role", String.class);
            AdminRole role = AdminRole.valueOf(roleStr);

            String tenantScopeStr = claims.get("tenant_scope", String.class);
            UUID tenantScope = (tenantScopeStr != null && !tenantScopeStr.isBlank())
                    ? UUID.fromString(tenantScopeStr)
                    : null;

            return Optional.of(new AdminPrincipal(id, email, role, tenantScope));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public SecretKey getSigningKey() {
        return signingKey;
    }

    public long getExpirySeconds() {
        return expirySeconds;
    }
}
