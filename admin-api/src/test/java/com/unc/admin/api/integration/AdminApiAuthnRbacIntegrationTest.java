package com.unc.admin.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unc.admin.api.dto.CreateAdminApiKeyRequest;
import com.unc.admin.api.dto.LoginRequest;
import com.unc.admin.api.dto.RefreshTokenRequest;
import com.unc.admin.api.dto.ServiceDto;
import com.unc.admin.api.security.AdminRole;
import com.unc.admin.api.security.RefreshTokenService;
import com.unc.admin.api.security.TenantScopeGuard;
import com.unc.admin.api.tenant.TenantContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("isPostgresAvailable")
@DisplayName("Phase 23: Admin API Authentication & RBAC Integration Tests")
class AdminApiAuthnRbacIntegrationTest {

    private static PostgreSQLContainer<?> postgresContainer;
    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static boolean postgresAvailable = false;

    private static final String TENANT_A_STR = "tenant-a";
    private static final String TENANT_B_STR = "tenant-b";

    private static final UUID TENANT_A_ID = TenantScopeGuard.parseTenantId(TENANT_A_STR);
    private static final UUID TENANT_B_ID = TenantScopeGuard.parseTenantId(TENANT_B_STR);

    private static final String VALID_MACHINE_KEY = "unc_adm_valid_m2m_key_test_123456";
    private static final String REVOKED_MACHINE_KEY = "unc_adm_revoked_m2m_key_test_123456";
    private static final String EXPIRED_MACHINE_KEY = "unc_adm_expired_m2m_key_test_123456";

    static {
        try {
            postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("unc_db")
                    .withUsername("postgres")
                    .withPassword("postgrespassword");
            postgresContainer.start();
            jdbcUrl = postgresContainer.getJdbcUrl();
            username = postgresContainer.getUsername();
            password = postgresContainer.getPassword();
            postgresAvailable = true;
        } catch (Throwable t) {
            String localUrl = "jdbc:postgresql://localhost:5435/unc_db";
            try (Connection conn = DriverManager.getConnection(localUrl, "postgres", "postgrespassword")) {
                jdbcUrl = localUrl;
                username = "postgres";
                password = "postgrespassword";
                postgresAvailable = true;
            } catch (SQLException ignored) {
                postgresAvailable = false;
            }
        }
    }

    static boolean isPostgresAvailable() {
        return postgresAvailable;
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgresAvailable) {
            registry.add("spring.datasource.url", () -> jdbcUrl);
            registry.add("spring.datasource.username", () -> username);
            registry.add("spring.datasource.password", () -> password);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
            registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.target", () -> "latest");
            registry.add("admin.jwt.secret", () -> "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
            registry.add("admin.jwt.expiry-seconds", () -> "900");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void setUpAll(@Autowired PasswordEncoder passwordEncoder) throws SQLException {
        Assumptions.assumeTrue(postgresAvailable, "A PostgreSQL instance is required for this integration test");

        // Run Flyway migrations through V8
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            // Seed Tenants
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO tenants (id, name, tenant_id, api_key, status) VALUES (?, ?, ?, ?, ?) " +
                            "ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE'")) {
                stmt.setObject(1, TENANT_A_ID);
                stmt.setString(2, "tenant-a");
                stmt.setObject(3, TENANT_A_ID);
                stmt.setString(4, "api-key-" + TENANT_A_ID);
                stmt.setString(5, "ACTIVE");
                stmt.executeUpdate();

                stmt.setObject(1, TENANT_B_ID);
                stmt.setString(2, "tenant-b");
                stmt.setObject(3, TENANT_B_ID);
                stmt.setString(4, "api-key-" + TENANT_B_ID);
                stmt.setString(5, "ACTIVE");
                stmt.executeUpdate();
            }

            // Seed Admin Users
            // 1. Bootstrap Admin (admin@unc.local / changeme)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO admin_users (id, email, password_hash, role, tenant_scope, enabled) " +
                            "VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT (email) DO UPDATE SET password_hash = ?, role = 'ADMIN', enabled = TRUE")) {
                String adminHash = passwordEncoder.encode("changeme");
                stmt.setObject(1, UUID.fromString("a0000000-0000-0000-0000-000000000001"));
                stmt.setString(2, "admin@unc.local");
                stmt.setString(3, adminHash);
                stmt.setString(4, "ADMIN");
                stmt.setObject(5, null);
                stmt.setBoolean(6, true);
                stmt.setString(7, adminHash);
                stmt.executeUpdate();
            }

            // 2. Operator User scoped to tenant-a (operator@unc.local / operatorpass)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO admin_users (id, email, password_hash, role, tenant_scope, enabled) " +
                            "VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT (email) DO UPDATE SET password_hash = ?, role = 'OPERATOR', tenant_scope = ?, enabled = TRUE")) {
                String opHash = passwordEncoder.encode("operatorpass");
                stmt.setObject(1, UUID.randomUUID());
                stmt.setString(2, "operator@unc.local");
                stmt.setString(3, opHash);
                stmt.setString(4, "OPERATOR");
                stmt.setObject(5, TENANT_A_ID);
                stmt.setBoolean(6, true);
                stmt.setString(7, opHash);
                stmt.setObject(8, TENANT_A_ID);
                stmt.executeUpdate();
            }

            // 3. Viewer User scoped to tenant-a (viewer@unc.local / viewerpass)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO admin_users (id, email, password_hash, role, tenant_scope, enabled) " +
                            "VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT (email) DO UPDATE SET password_hash = ?, role = 'VIEWER', tenant_scope = ?, enabled = TRUE")) {
                String viewerHash = passwordEncoder.encode("viewerpass");
                stmt.setObject(1, UUID.randomUUID());
                stmt.setString(2, "viewer@unc.local");
                stmt.setString(3, viewerHash);
                stmt.setString(4, "VIEWER");
                stmt.setObject(5, TENANT_A_ID);
                stmt.setBoolean(6, true);
                stmt.setString(7, viewerHash);
                stmt.setObject(8, TENANT_A_ID);
                stmt.executeUpdate();
            }

            // Seed Machine API Keys
            // Valid Key
            String validHash = RefreshTokenService.hashToken(VALID_MACHINE_KEY);
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO admin_api_keys (id, key_hash, label, role, tenant_scope, expires_at, revoked) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT (key_hash) DO UPDATE SET expires_at = ?, revoked = FALSE")) {
                Timestamp future = Timestamp.from(Instant.now().plus(2, ChronoUnit.DAYS));
                stmt.setObject(1, UUID.randomUUID());
                stmt.setString(2, validHash);
                stmt.setString(3, "valid-ci-key");
                stmt.setString(4, "OPERATOR");
                stmt.setObject(5, TENANT_A_ID);
                stmt.setTimestamp(6, future);
                stmt.setBoolean(7, false);
                stmt.setTimestamp(8, future);
                stmt.executeUpdate();
            }

            // Revoked Key
            String revokedHash = RefreshTokenService.hashToken(REVOKED_MACHINE_KEY);
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO admin_api_keys (id, key_hash, label, role, tenant_scope, expires_at, revoked) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT (key_hash) DO UPDATE SET revoked = TRUE")) {
                Timestamp future = Timestamp.from(Instant.now().plus(2, ChronoUnit.DAYS));
                stmt.setObject(1, UUID.randomUUID());
                stmt.setString(2, revokedHash);
                stmt.setString(3, "revoked-ci-key");
                stmt.setString(4, "OPERATOR");
                stmt.setObject(5, TENANT_A_ID);
                stmt.setTimestamp(6, future);
                stmt.setBoolean(7, true);
                stmt.executeUpdate();
            }

            // Expired Key
            String expiredHash = RefreshTokenService.hashToken(EXPIRED_MACHINE_KEY);
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO admin_api_keys (id, key_hash, label, role, tenant_scope, expires_at, revoked) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT (key_hash) DO UPDATE SET expires_at = ?, revoked = FALSE")) {
                Timestamp past = Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS));
                stmt.setObject(1, UUID.randomUUID());
                stmt.setString(2, expiredHash);
                stmt.setString(3, "expired-ci-key");
                stmt.setString(4, "OPERATOR");
                stmt.setObject(5, TENANT_A_ID);
                stmt.setTimestamp(6, past);
                stmt.setBoolean(7, false);
                stmt.setTimestamp(8, past);
                stmt.executeUpdate();
            }
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private JsonNode login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        MvcResult res = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("(1) Bootstrap login returns valid JWT and authenticated admin call to GET /api/admin/services returns 200")
    void testBootstrapLoginAndAdminServicesAccess() throws Exception {
        JsonNode auth = login("admin@unc.local", "changeme");
        String accessToken = auth.get("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(auth.get("refreshToken").asText()).isNotBlank();
        assertThat(auth.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(auth.get("expiresIn").asInt()).isEqualTo(900);

        // Authenticated admin call with X-Tenant-Id
        mockMvc.perform(get("/api/admin/services")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unauthenticated call to /api/admin/services returns HTTP 401 Unauthorized")
    void testUnauthenticatedCallReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/services")
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid login credentials return HTTP 401 Unauthorized")
    void testInvalidLoginCredentials() throws Exception {
        LoginRequest req = new LoginRequest("admin@unc.local", "wrongpassword");
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("(2) Exchanging refresh token returns new access JWT and rotates refresh token")
    void testRefreshTokenExchangeAndRotation() throws Exception {
        JsonNode loginAuth = login("admin@unc.local", "changeme");
        String firstRefreshToken = loginAuth.get("refreshToken").asText();

        // Exchange refresh token
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(firstRefreshToken);
        MvcResult res = mockMvc.perform(post("/api/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode refreshAuth = objectMapper.readTree(res.getResponse().getContentAsString());
        String newAccessToken = refreshAuth.get("accessToken").asText();
        String newRefreshToken = refreshAuth.get("refreshToken").asText();

        assertThat(newRefreshToken).isNotEqualTo(firstRefreshToken);

        // New access token is functional
        mockMvc.perform(get("/api/admin/services")
                        .header("Authorization", "Bearer " + newAccessToken)
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isOk());

        // Replaying the old (rotated) refresh token must fail with 401
        mockMvc.perform(post("/api/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(firstRefreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("(3) Machine API key authentication: valid key returns 200; revoked and expired return 401")
    void testMachineApiKeyAuthentication() throws Exception {
        // Valid machine API key
        mockMvc.perform(get("/api/admin/services")
                        .header("X-Admin-Api-Key", VALID_MACHINE_KEY)
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isOk());

        // Revoked machine API key -> 401
        mockMvc.perform(get("/api/admin/services")
                        .header("X-Admin-Api-Key", REVOKED_MACHINE_KEY)
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isUnauthorized());

        // Expired machine API key -> 401
        mockMvc.perform(get("/api/admin/services")
                        .header("X-Admin-Api-Key", EXPIRED_MACHINE_KEY)
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("(4) RBAC Matrix: ADMIN has full CRUD on both tenant-a and tenant-b")
    void testAdminRoleFullCrudOnBothTenants() throws Exception {
        String token = login("admin@unc.local", "changeme").get("accessToken").asText();

        for (String tenant : List.of(TENANT_A_STR, TENANT_B_STR)) {
            // GET list
            mockMvc.perform(get("/api/admin/services")
                            .header("Authorization", "Bearer " + token)
                            .header("X-Tenant-Id", tenant))
                    .andExpect(status().isOk());

            // POST create
            ServiceDto createDto = new ServiceDto();
            createDto.setName("svc-admin-" + tenant + "-" + UUID.randomUUID().toString().substring(0, 8));
            createDto.setUpstreamUrl("http://upstream:8080");

            MvcResult createResult = mockMvc.perform(post("/api/admin/services")
                            .header("Authorization", "Bearer " + token)
                            .header("X-Tenant-Id", tenant)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createDto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andReturn();

            String serviceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

            // GET single
            mockMvc.perform(get("/api/admin/services/" + serviceId)
                            .header("Authorization", "Bearer " + token)
                            .header("X-Tenant-Id", tenant))
                    .andExpect(status().isOk());

            // PUT update
            createDto.setName("svc-admin-" + tenant + "-updated");
            mockMvc.perform(put("/api/admin/services/" + serviceId)
                            .header("Authorization", "Bearer " + token)
                            .header("X-Tenant-Id", tenant)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createDto)))
                    .andExpect(status().isOk());

            // DELETE
            mockMvc.perform(delete("/api/admin/services/" + serviceId)
                            .header("Authorization", "Bearer " + token)
                            .header("X-Tenant-Id", tenant))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @DisplayName("(5) RBAC Matrix: OPERATOR (scoped to tenant-a) has full CRUD on tenant-a, rejected with 403 on tenant-b")
    void testOperatorRoleCrudOnTenantAAndForbiddenOnTenantB() throws Exception {
        String token = login("operator@unc.local", "operatorpass").get("accessToken").asText();

        // CRUD on tenant-a -> all succeed
        ServiceDto createDto = new ServiceDto();
        createDto.setName("svc-op-a-" + UUID.randomUUID().toString().substring(0, 8));
        createDto.setUpstreamUrl("http://upstream:8080");

        MvcResult createResult = mockMvc.perform(post("/api/admin/services")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_A_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn();

        String serviceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/admin/services")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/services/" + serviceId)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isOk());

        createDto.setName("svc-op-a-updated");
        mockMvc.perform(put("/api/admin/services/" + serviceId)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_A_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/services/" + serviceId)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isNoContent());

        // Operations on tenant-b -> rejected with 403 Forbidden
        mockMvc.perform(get("/api/admin/services")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_B_STR))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/services")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_B_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/services/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_B_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/services/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_B_STR))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("(6) RBAC Matrix: VIEWER (scoped to tenant-a) has read-only on tenant-a, mutations return 403; tenant-b returns 403")
    void testViewerRoleReadOnlyOnTenantAAndForbiddenOnTenantB() throws Exception {
        String token = login("viewer@unc.local", "viewerpass").get("accessToken").asText();

        // Read on tenant-a -> 200 OK
        mockMvc.perform(get("/api/admin/services")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isOk());

        // Mutations on tenant-a -> 403 Forbidden
        ServiceDto createDto = new ServiceDto();
        createDto.setName("svc-viewer-attempt");
        createDto.setUpstreamUrl("http://upstream:8080");

        mockMvc.perform(post("/api/admin/services")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_A_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/services/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_A_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/services/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isForbidden());

        // Any operations on tenant-b -> 403 Forbidden
        mockMvc.perform(get("/api/admin/services")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_B_STR))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/services")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_B_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("(7) ADMIN can issue machine API key via POST /api/admin/auth/admin-keys and key is usable; VIEWER is rejected")
    void testIssueMachineApiKey() throws Exception {
        String adminToken = login("admin@unc.local", "changeme").get("accessToken").asText();
        String viewerToken = login("viewer@unc.local", "viewerpass").get("accessToken").asText();

        // VIEWER attempting to create admin key receives 403
        CreateAdminApiKeyRequest viewerAttempt = new CreateAdminApiKeyRequest("fail-key", AdminRole.OPERATOR, TENANT_A_STR);
        mockMvc.perform(post("/api/admin/auth/admin-keys")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(viewerAttempt)))
                .andExpect(status().isForbidden());

        // ADMIN creating admin key succeeds
        CreateAdminApiKeyRequest adminReq = new CreateAdminApiKeyRequest("dynamic-ci-key", AdminRole.OPERATOR, TENANT_A_STR);
        MvcResult res = mockMvc.perform(post("/api/admin/auth/admin-keys")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").isNotEmpty())
                .andExpect(jsonPath("$.label").value("dynamic-ci-key"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andReturn();

        String rawKey = objectMapper.readTree(res.getResponse().getContentAsString()).get("key").asText();
        assertThat(rawKey).startsWith("unc_adm_");

        // Newly issued machine key is immediately operational
        mockMvc.perform(get("/api/admin/services")
                        .header("X-Admin-Api-Key", rawKey)
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isOk());
    }
}
