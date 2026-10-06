package com.unc.admin.api.auth;

import com.unc.admin.api.dto.*;
import com.unc.admin.api.entity.AdminApiKeyEntity;
import com.unc.admin.api.entity.AdminUserEntity;
import com.unc.admin.api.repository.AdminApiKeyRepository;
import com.unc.admin.api.repository.AdminUserRepository;
import com.unc.admin.api.security.AdminPrincipal;
import com.unc.admin.api.security.JwtService;
import com.unc.admin.api.security.RefreshTokenService;
import com.unc.admin.api.security.TenantScopeGuard;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/auth")
public class AuthController {

    private final AdminUserRepository adminUserRepository;
    private final AdminApiKeyRepository adminApiKeyRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            AdminUserRepository adminUserRepository,
            AdminApiKeyRepository adminApiKeyRepository,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.adminApiKeyRepository = adminApiKeyRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        if (request == null || request.getEmail() == null || request.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email and password are required");
        }

        AdminUserEntity user = adminUserRepository.findByEmail(request.getEmail().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!user.isEnabled() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        AdminPrincipal principal = new AdminPrincipal(user.getId(), user.getEmail(), user.getRole(), user.getTenantScope());
        String accessToken = jwtService.issueToken(principal);
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, "Bearer", jwtService.getExpirySeconds()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is required");
        }
        AuthResponse response = refreshTokenService.exchangeRefreshToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminApiKeyResponse> createAdminKey(@RequestBody CreateAdminApiKeyRequest request) {
        if (request == null || request.getLabel() == null || request.getLabel().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Label is required");
        }
        if (request.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        }

        String rawKey = "unc_adm_" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String keyHash = RefreshTokenService.hashToken(rawKey);

        UUID tenantScope = null;
        if (request.getTenantScope() != null && !request.getTenantScope().isBlank()) {
            tenantScope = TenantScopeGuard.parseTenantId(request.getTenantScope());
        }

        AdminApiKeyEntity entity = new AdminApiKeyEntity();
        entity.setKeyHash(keyHash);
        entity.setLabel(request.getLabel().trim());
        entity.setRole(request.getRole());
        entity.setTenantScope(tenantScope);
        entity.setExpiresAt(request.getExpiresAt());
        entity.setRevoked(false);
        entity.setCreatedAt(OffsetDateTime.now());

        AdminApiKeyEntity saved = adminApiKeyRepository.save(entity);

        AdminApiKeyResponse response = new AdminApiKeyResponse(
                saved.getId(),
                rawKey,
                saved.getLabel(),
                saved.getRole(),
                saved.getTenantScope() != null ? saved.getTenantScope().toString() : null,
                saved.getExpiresAt(),
                saved.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
