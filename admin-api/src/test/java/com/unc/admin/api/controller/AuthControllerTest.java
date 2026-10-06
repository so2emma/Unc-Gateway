package com.unc.admin.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unc.admin.api.dto.CreateAdminApiKeyRequest;
import com.unc.admin.api.dto.LoginRequest;
import com.unc.admin.api.dto.RefreshTokenRequest;
import com.unc.admin.api.entity.AdminApiKeyEntity;
import com.unc.admin.api.entity.AdminUserEntity;
import com.unc.admin.api.entity.RefreshTokenEntity;
import com.unc.admin.api.repository.AdminApiKeyRepository;
import com.unc.admin.api.repository.AdminUserRepository;
import com.unc.admin.api.repository.RefreshTokenRepository;
import com.unc.admin.api.security.AdminPrincipal;
import com.unc.admin.api.security.AdminRole;
import com.unc.admin.api.security.JwtService;
import com.unc.admin.api.security.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Phase 23: AuthController Unit & MockMvc Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AdminUserRepository adminUserRepository;

    @MockBean
    private AdminApiKeyRepository adminApiKeyRepository;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("POST /api/admin/auth/login - valid credentials return accessToken and refreshToken")
    void testLoginSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        String hash = passwordEncoder.encode("secret123");
        AdminUserEntity user = new AdminUserEntity(
                userId, "admin@unc.local", hash, AdminRole.ADMIN, null, true
        );

        given(adminUserRepository.findByEmail("admin@unc.local")).willReturn(Optional.of(user));
        given(refreshTokenRepository.save(any(RefreshTokenEntity.class))).willAnswer(i -> i.getArgument(0));

        LoginRequest loginRequest = new LoginRequest("admin@unc.local", "secret123");

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("POST /api/admin/auth/login - invalid credentials return HTTP 401")
    void testLoginInvalidCredentials() throws Exception {
        UUID userId = UUID.randomUUID();
        String hash = passwordEncoder.encode("correct-password");
        AdminUserEntity user = new AdminUserEntity(
                userId, "user@unc.local", hash, AdminRole.OPERATOR, null, true
        );

        given(adminUserRepository.findByEmail("user@unc.local")).willReturn(Optional.of(user));

        LoginRequest loginRequest = new LoginRequest("user@unc.local", "wrong-password");

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/admin/auth/login - non-existent user returns HTTP 401")
    void testLoginUserNotFound() throws Exception {
        given(adminUserRepository.findByEmail("nonexistent@unc.local")).willReturn(Optional.empty());

        LoginRequest loginRequest = new LoginRequest("nonexistent@unc.local", "any");

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/admin/auth/refresh - valid refresh token rotates and returns new tokens")
    void testRefreshSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminUserEntity user = new AdminUserEntity(
                userId, "user@unc.local", "hash", AdminRole.OPERATOR, UUID.randomUUID(), true
        );
        given(adminUserRepository.findById(userId)).willReturn(Optional.of(user));

        String rawToken = "unc_ref_existingtoken1234567890";
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        RefreshTokenEntity entity = new RefreshTokenEntity(
                UUID.randomUUID(), tokenHash, userId, OffsetDateTime.now().plusHours(12), false
        );

        given(refreshTokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.of(entity));
        given(refreshTokenRepository.save(any(RefreshTokenEntity.class))).willAnswer(i -> i.getArgument(0));

        RefreshTokenRequest req = new RefreshTokenRequest(rawToken);

        mockMvc.perform(post("/api/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    @DisplayName("POST /api/admin/auth/admin-keys - VIEWER principal receives HTTP 403 Forbidden")
    void testViewerCannotCreateAdminKeys() throws Exception {
        AdminPrincipal viewer = new AdminPrincipal(UUID.randomUUID(), "viewer@unc.local", AdminRole.VIEWER, UUID.randomUUID());
        String viewerToken = jwtService.issueToken(viewer);

        CreateAdminApiKeyRequest req = new CreateAdminApiKeyRequest("test-key", AdminRole.OPERATOR, "tenant-a");

        mockMvc.perform(post("/api/admin/auth/admin-keys")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/admin/auth/admin-keys - ADMIN principal creates key and receives raw key in 201 response")
    void testAdminCanCreateAdminKey() throws Exception {
        AdminPrincipal admin = new AdminPrincipal(UUID.randomUUID(), "admin@unc.local", AdminRole.ADMIN, null);
        String adminToken = jwtService.issueToken(admin);

        given(adminApiKeyRepository.save(any(AdminApiKeyEntity.class))).willAnswer(i -> {
            AdminApiKeyEntity toSave = i.getArgument(0);
            toSave.setId(UUID.randomUUID());
            return toSave;
        });

        CreateAdminApiKeyRequest req = new CreateAdminApiKeyRequest("ci-pipeline", AdminRole.OPERATOR, "tenant-a");

        mockMvc.perform(post("/api/admin/auth/admin-keys")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").isString())
                .andExpect(jsonPath("$.label").value("ci-pipeline"))
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }
}
