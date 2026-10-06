package com.unc.admin.api.security;

import com.unc.admin.api.dto.AuthResponse;
import com.unc.admin.api.entity.AdminUserEntity;
import com.unc.admin.api.entity.RefreshTokenEntity;
import com.unc.admin.api.repository.AdminUserRepository;
import com.unc.admin.api.repository.RefreshTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminUserRepository adminUserRepository;
    private final JwtService jwtService;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AdminUserRepository adminUserRepository,
            JwtService jwtService) {
        this(refreshTokenRepository, adminUserRepository, jwtService, Clock.systemUTC());
    }

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AdminUserRepository adminUserRepository,
            JwtService jwtService,
            Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.adminUserRepository = adminUserRepository;
        this.jwtService = jwtService;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public String createRefreshToken(UUID userId) {
        String rawToken = "unc_ref_" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String tokenHash = hashToken(rawToken);

        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setTokenHash(tokenHash);
        entity.setUserId(userId);
        entity.setExpiresAt(OffsetDateTime.now(clock).plusHours(24));
        entity.setRevoked(false);
        entity.setCreatedAt(OffsetDateTime.now(clock));

        refreshTokenRepository.save(entity);
        return rawToken;
    }

    public AuthResponse exchangeRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is required");
        }
        String tokenHash = hashToken(rawToken);
        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (tokenEntity.isRevoked() || tokenEntity.getExpiresAt().isBefore(OffsetDateTime.now(clock))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is expired or revoked");
        }

        // Token rotation: revoke old token
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);

        AdminUserEntity user = adminUserRepository.findById(tokenEntity.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account is disabled");
        }

        AdminPrincipal principal = new AdminPrincipal(user.getId(), user.getEmail(), user.getRole(), user.getTenantScope());
        String newAccessToken = jwtService.issueToken(principal);
        String newRefreshToken = createRefreshToken(user.getId());

        return new AuthResponse(newAccessToken, newRefreshToken, "Bearer", jwtService.getExpirySeconds());
    }

    public static String hashToken(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
