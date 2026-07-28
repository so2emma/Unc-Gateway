package com.unc.admin.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unc.admin.api.dto.ServiceDto;
import com.unc.admin.api.entity.ServiceEntity;
import com.unc.admin.api.repository.ServiceRepository;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ServiceRepository serviceRepository;

    @MockBean
    private TenantRepository tenantRepository;

    private static final UUID TENANT_A = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID SERVICE_ID = UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380b22");

    @Test
    @DisplayName("POST /api/admin/services - missing X-Tenant-Id header returns 400 Bad Request")
    void testCreateServiceMissingTenantHeader() throws Exception {
        ServiceDto dto = new ServiceDto();
        dto.setName("demo-service");
        dto.setUpstreamUrl("http://mock-upstream:9090");

        mockMvc.perform(post("/api/admin/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/admin/services - valid request creates tenant-scoped service")
    void testCreateServiceSuccess() throws Exception {
        given(tenantRepository.existsById(TENANT_A)).willReturn(true);

        ServiceEntity saved = new ServiceEntity();
        saved.setId(SERVICE_ID);
        saved.setTenantId(TENANT_A);
        saved.setName("demo-service");
        saved.setUrl("http://mock-upstream:9090");

        given(serviceRepository.save(any(ServiceEntity.class))).willReturn(saved);

        ServiceDto dto = new ServiceDto();
        dto.setName("demo-service");
        dto.setUpstreamUrl("http://mock-upstream:9090");

        mockMvc.perform(post("/api/admin/services")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SERVICE_ID.toString()))
                .andExpect(jsonPath("$.name").value("demo-service"))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A.toString()))
                .andExpect(jsonPath("$.upstreamUrl").value("http://mock-upstream:9090"));
    }

    @Test
    @DisplayName("GET /api/admin/services - returns services filtered by X-Tenant-Id")
    void testListServicesTenantScoped() throws Exception {
        given(tenantRepository.existsById(TENANT_A)).willReturn(true);

        ServiceEntity srv = new ServiceEntity();
        srv.setId(SERVICE_ID);
        srv.setTenantId(TENANT_A);
        srv.setName("demo-service");
        srv.setUrl("http://mock-upstream:9090");

        given(serviceRepository.findByTenantId(TENANT_A)).willReturn(List.of(srv));

        mockMvc.perform(get("/api/admin/services")
                        .header("X-Tenant-Id", TENANT_A.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(SERVICE_ID.toString()))
                .andExpect(jsonPath("$[0].tenantId").value(TENANT_A.toString()));

        verify(serviceRepository).findByTenantId(TENANT_A);
    }

    @Test
    @DisplayName("DELETE /api/admin/services/{id} - deletes service scoped by tenant_id")
    void testDeleteServiceSuccess() throws Exception {
        given(tenantRepository.existsById(TENANT_A)).willReturn(true);
        given(serviceRepository.existsByIdAndTenantId(SERVICE_ID, TENANT_A)).willReturn(true);

        mockMvc.perform(delete("/api/admin/services/" + SERVICE_ID)
                        .header("X-Tenant-Id", TENANT_A.toString()))
                .andExpect(status().isNoContent());

        verify(serviceRepository).deleteByIdAndTenantId(SERVICE_ID, TENANT_A);
    }
}
