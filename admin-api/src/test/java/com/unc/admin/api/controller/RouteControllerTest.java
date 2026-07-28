package com.unc.admin.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unc.admin.api.dto.RouteDto;
import com.unc.admin.api.entity.RouteEntity;
import com.unc.admin.api.repository.RouteRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RouteRepository routeRepository;

    @MockBean
    private ServiceRepository serviceRepository;

    @MockBean
    private TenantRepository tenantRepository;

    @Test
    @DisplayName("POST /api/admin/routes - valid request creates tenant-scoped route")
    void testCreateRouteSuccess() throws Exception {
        given(tenantRepository.existsById("tenant-a")).willReturn(true);
        given(serviceRepository.existsByIdAndTenantId("srv-123", "tenant-a")).willReturn(true);

        RouteEntity saved = new RouteEntity();
        saved.setId("route-123");
        saved.setTenantId("tenant-a");
        saved.setServiceId("srv-123");
        saved.setPaths("/demo");
        saved.setName("demo-route");

        given(routeRepository.save(any(RouteEntity.class))).willReturn(saved);

        RouteDto dto = new RouteDto();
        dto.setServiceId("srv-123");
        dto.setPath("/demo");

        mockMvc.perform(post("/api/admin/routes")
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("route-123"))
                .andExpect(jsonPath("$.serviceId").value("srv-123"))
                .andExpect(jsonPath("$.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.path").value("/demo"));
    }

    @Test
    @DisplayName("GET /api/admin/routes - returns routes filtered by X-Tenant-Id")
    void testListRoutesTenantScoped() throws Exception {
        given(tenantRepository.existsById("tenant-a")).willReturn(true);

        RouteEntity route = new RouteEntity();
        route.setId("route-123");
        route.setTenantId("tenant-a");
        route.setServiceId("srv-123");
        route.setPaths("/demo");

        given(routeRepository.findByTenantId("tenant-a")).willReturn(List.of(route));

        mockMvc.perform(get("/api/admin/routes")
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("route-123"))
                .andExpect(jsonPath("$[0].tenantId").value("tenant-a"));

        verify(routeRepository).findByTenantId("tenant-a");
    }
}
