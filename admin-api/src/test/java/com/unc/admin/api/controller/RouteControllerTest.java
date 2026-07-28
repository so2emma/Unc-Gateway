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
import java.util.UUID;

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

    private static final UUID TENANT_A = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID SERVICE_ID = UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380b22");
    private static final UUID ROUTE_ID = UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380c33");

    @Test
    @DisplayName("POST /api/admin/routes - valid request creates tenant-scoped route")
    void testCreateRouteSuccess() throws Exception {
        given(tenantRepository.existsById(TENANT_A)).willReturn(true);
        given(serviceRepository.existsByIdAndTenantId(SERVICE_ID, TENANT_A)).willReturn(true);

        RouteEntity saved = new RouteEntity();
        saved.setId(ROUTE_ID);
        saved.setTenantId(TENANT_A);
        saved.setServiceId(SERVICE_ID);
        saved.setPaths("/demo");
        saved.setName("demo-route");

        given(routeRepository.save(any(RouteEntity.class))).willReturn(saved);

        RouteDto dto = new RouteDto();
        dto.setServiceId(SERVICE_ID);
        dto.setPath("/demo");

        mockMvc.perform(post("/api/admin/routes")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ROUTE_ID.toString()))
                .andExpect(jsonPath("$.serviceId").value(SERVICE_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A.toString()))
                .andExpect(jsonPath("$.path").value("/demo"));
    }

    @Test
    @DisplayName("GET /api/admin/routes - returns routes filtered by X-Tenant-Id")
    void testListRoutesTenantScoped() throws Exception {
        given(tenantRepository.existsById(TENANT_A)).willReturn(true);

        RouteEntity route = new RouteEntity();
        route.setId(ROUTE_ID);
        route.setTenantId(TENANT_A);
        route.setServiceId(SERVICE_ID);
        route.setPaths("/demo");

        given(routeRepository.findByTenantId(TENANT_A)).willReturn(List.of(route));

        mockMvc.perform(get("/api/admin/routes")
                        .header("X-Tenant-Id", TENANT_A.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ROUTE_ID.toString()))
                .andExpect(jsonPath("$[0].tenantId").value(TENANT_A.toString()));

        verify(routeRepository).findByTenantId(TENANT_A);
    }
}
