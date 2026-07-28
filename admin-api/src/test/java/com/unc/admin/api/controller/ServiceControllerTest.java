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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        given(tenantRepository.existsById("tenant-a")).willReturn(true);

        ServiceEntity saved = new ServiceEntity();
        saved.setId("srv-123");
        saved.setTenantId("tenant-a");
        saved.setName("demo-service");
        saved.setUrl("http://mock-upstream:9090");

        given(serviceRepository.save(any(ServiceEntity.class))).willReturn(saved);

        ServiceDto dto = new ServiceDto();
        dto.setName("demo-service");
        dto.setUpstreamUrl("http://mock-upstream:9090");

        mockMvc.perform(post("/api/admin/services")
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("srv-123"))
                .andExpect(jsonPath("$.name").value("demo-service"))
                .andExpect(jsonPath("$.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.upstreamUrl").value("http://mock-upstream:9090"));
    }

    @Test
    @DisplayName("GET /api/admin/services - returns services filtered by X-Tenant-Id")
    void testListServicesTenantScoped() throws Exception {
        given(tenantRepository.existsById("tenant-a")).willReturn(true);

        ServiceEntity srv = new ServiceEntity();
        srv.setId("srv-123");
        srv.setTenantId("tenant-a");
        srv.setName("demo-service");
        srv.setUrl("http://mock-upstream:9090");

        given(serviceRepository.findByTenantId("tenant-a")).willReturn(List.of(srv));

        mockMvc.perform(get("/api/admin/services")
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("srv-123"))
                .andExpect(jsonPath("$[0].tenantId").value("tenant-a"));

        verify(serviceRepository).findByTenantId("tenant-a");
    }

    @Test
    @DisplayName("DELETE /api/admin/services/{id} - deletes service scoped by tenant_id")
    void testDeleteServiceSuccess() throws Exception {
        given(tenantRepository.existsById("tenant-a")).willReturn(true);
        given(serviceRepository.existsByIdAndTenantId("srv-123", "tenant-a")).willReturn(true);

        mockMvc.perform(delete("/api/admin/services/srv-123")
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isNoContent());

        verify(serviceRepository).deleteByIdAndTenantId("srv-123", "tenant-a");
    }
}
