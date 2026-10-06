package com.unc.admin.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unc.admin.api.dto.ServiceDto;
import com.unc.admin.api.entity.ServiceEntity;
import com.unc.admin.api.repository.ServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Phase 23: RBAC Method Security Unit & MockMvc Tests")
class RbacMethodSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private ServiceRepository serviceRepository;

    private static final String TENANT_A_STR = "tenant-a";
    private static final UUID TENANT_A = TenantScopeGuard.parseTenantId(TENANT_A_STR);

    @Test
    @DisplayName("VIEWER-authenticated request to POST /api/admin/services returns HTTP 403 Forbidden")
    void testViewerPostServicesReturnsForbidden() throws Exception {
        AdminPrincipal viewer = new AdminPrincipal(UUID.randomUUID(), "viewer@unc.local", AdminRole.VIEWER, TENANT_A);
        String token = jwtService.issueToken(viewer);

        ServiceDto dto = new ServiceDto();
        dto.setName("new-service");
        dto.setUpstreamUrl("http://mock-upstream:9090");

        mockMvc.perform(post("/api/admin/services")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_A_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OPERATOR-authenticated request to POST /api/admin/services returns HTTP 201 Created")
    void testOperatorPostServicesReturnsCreated() throws Exception {
        AdminPrincipal operator = new AdminPrincipal(UUID.randomUUID(), "operator@unc.local", AdminRole.OPERATOR, TENANT_A);
        String token = jwtService.issueToken(operator);

        ServiceEntity saved = new ServiceEntity();
        saved.setId(UUID.randomUUID());
        saved.setTenantId(TENANT_A);
        saved.setName("new-service");
        saved.setUrl("http://mock-upstream:9090");
        given(serviceRepository.save(any(ServiceEntity.class))).willReturn(saved);

        ServiceDto dto = new ServiceDto();
        dto.setName("new-service");
        dto.setUpstreamUrl("http://mock-upstream:9090");

        mockMvc.perform(post("/api/admin/services")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_A_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("new-service"));
    }

    @Test
    @DisplayName("VIEWER-authenticated request to GET /api/admin/services returns HTTP 200 OK")
    void testViewerGetServicesReturnsOk() throws Exception {
        AdminPrincipal viewer = new AdminPrincipal(UUID.randomUUID(), "viewer@unc.local", AdminRole.VIEWER, TENANT_A);
        String token = jwtService.issueToken(viewer);

        given(serviceRepository.findByTenantId(TENANT_A)).willReturn(List.of());

        mockMvc.perform(get("/api/admin/services")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("VIEWER-authenticated request to DELETE /api/admin/services/{id} returns HTTP 403 Forbidden")
    void testViewerDeleteServicesReturnsForbidden() throws Exception {
        AdminPrincipal viewer = new AdminPrincipal(UUID.randomUUID(), "viewer@unc.local", AdminRole.VIEWER, TENANT_A);
        String token = jwtService.issueToken(viewer);

        mockMvc.perform(delete("/api/admin/services/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", TENANT_A_STR))
                .andExpect(status().isForbidden());
    }
}
