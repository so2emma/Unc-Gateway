package com.unc.admin.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unc.admin.api.dto.CreateTenantRequest;
import com.unc.admin.api.entity.TenantEntity;
import com.unc.admin.api.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TenantRepository tenantRepository;

    private static final UUID TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final String API_KEY = "unc_live_sec_1234567890abcdef";

    @Test
    @DisplayName("POST /api/admin/tenants - creates new tenant and generates API Key without authentication")
    void testCreateTenantSuccess() throws Exception {
        TenantEntity saved = new TenantEntity(TENANT_ID, "Acme Corp", API_KEY, "admin@acme.com");
        given(tenantRepository.save(any(TenantEntity.class))).willReturn(saved);

        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "admin@acme.com");

        mockMvc.perform(post("/api/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.email").value("admin@acme.com"))
                .andExpect(jsonPath("$.apiKey").value(API_KEY))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/admin/tenants - returns list of tenants when authenticated")
    void testListTenantsSuccess() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_ID, API_KEY, "ACTIVE")).willReturn(true);

        TenantEntity tenant = new TenantEntity(TENANT_ID, "Acme Corp", API_KEY, "admin@acme.com");
        given(tenantRepository.findAll()).willReturn(List.of(tenant));

        mockMvc.perform(get("/api/admin/tenants")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Acme Corp"));
    }

    @Test
    @DisplayName("GET /api/admin/tenants/{id} - returns tenant details")
    void testGetTenantSuccess() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_ID, API_KEY, "ACTIVE")).willReturn(true);

        TenantEntity tenant = new TenantEntity(TENANT_ID, "Acme Corp", API_KEY, "admin@acme.com");
        given(tenantRepository.findById(TENANT_ID)).willReturn(Optional.of(tenant));

        mockMvc.perform(get("/api/admin/tenants/" + TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.name").value("Acme Corp"));
    }

    @Test
    @DisplayName("DELETE /api/admin/tenants/{id} - deletes tenant when authenticated")
    void testDeleteTenantSuccess() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_ID, API_KEY, "ACTIVE")).willReturn(true);
        given(tenantRepository.existsById(TENANT_ID)).willReturn(true);

        mockMvc.perform(delete("/api/admin/tenants/" + TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isNoContent());

        verify(tenantRepository).deleteById(TENANT_ID);
    }
}
