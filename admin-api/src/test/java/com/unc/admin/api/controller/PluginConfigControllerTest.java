package com.unc.admin.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unc.admin.api.entity.PluginConfigEntity;
import com.unc.admin.api.repository.PluginConfigRepository;
import com.unc.admin.api.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PluginConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PluginConfigRepository pluginConfigRepository;

    @MockBean
    private TenantRepository tenantRepository;

    private static final UUID TENANT_A = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final String API_KEY_A = "unc_live_sec_1234567890abcdef";
    private static final UUID CONFIG_ID = UUID.fromString("f5eebc99-9c0b-4ef8-bb6d-6bb9bd380f66");

    @Test
    @DisplayName("POST /api/admin/plugin-configs - a registered plugin with a valid config returns 201")
    void testCreateKeyAuthConfigAccepted() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);
        given(pluginConfigRepository.save(any(PluginConfigEntity.class))).willAnswer(invocation -> {
            PluginConfigEntity toSave = invocation.getArgument(0);
            toSave.setId(CONFIG_ID);
            return toSave;
        });

        mockMvc.perform(post("/api/admin/plugin-configs")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pluginName\":\"key-auth\",\"enabled\":true,\"config\":{}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CONFIG_ID.toString()))
                .andExpect(jsonPath("$.pluginName").value("key-auth"))
                .andExpect(jsonPath("$.name").value("key-auth"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A.toString()));
    }

    @Test
    @DisplayName("POST /api/admin/plugin-configs - an unregistered plugin name returns 400 Bad Request")
    void testCreateUnknownPluginRejected() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);

        mockMvc.perform(post("/api/admin/plugin-configs")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pluginName\":\"not-a-plugin\",\"config\":{}}"))
                .andExpect(status().isBadRequest());

        verify(pluginConfigRepository, never()).save(any(PluginConfigEntity.class));
    }

    @Test
    @DisplayName("POST /api/admin/plugin-configs - a payload failing the plugin schema returns 400 Bad Request")
    void testCreateSchemaViolationRejected() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);

        mockMvc.perform(post("/api/admin/plugin-configs")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pluginName\":\"rate-limit\",\"config\":{\"limit\":0,\"window_seconds\":60}}"))
                .andExpect(status().isBadRequest());

        verify(pluginConfigRepository, never()).save(any(PluginConfigEntity.class));
    }

    @Test
    @DisplayName("GET /api/admin/plugin-configs - returns only the authenticated tenant's configs")
    void testListPluginConfigsTenantScoped() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);

        PluginConfigEntity entity = new PluginConfigEntity();
        entity.setId(CONFIG_ID);
        entity.setTenantId(TENANT_A);
        entity.setName("key-auth");
        entity.setOrdering(0);
        entity.setEnabled(true);
        entity.setConfig(new LinkedHashMap<>(Map.of("key_names", List.of("X-API-Key"))));

        given(pluginConfigRepository.findByTenantId(TENANT_A)).willReturn(List.of(entity));

        mockMvc.perform(get("/api/admin/plugin-configs")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(CONFIG_ID.toString()))
                .andExpect(jsonPath("$[0].pluginName").value("key-auth"))
                .andExpect(jsonPath("$[0].config.key_names[0]").value("X-API-Key"))
                .andExpect(jsonPath("$[0].tenantId").value(TENANT_A.toString()));

        verify(pluginConfigRepository).findByTenantId(TENANT_A);
    }

    @Test
    @DisplayName("GET /api/admin/plugin-configs/{id} - a config of another tenant returns 404 Not Found")
    void testGetForeignConfigReturns404() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);
        given(pluginConfigRepository.findByIdAndTenantId(CONFIG_ID, TENANT_A)).willReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/admin/plugin-configs/" + CONFIG_ID)
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/admin/plugin-configs/{id} - deletes scoped by tenant_id")
    void testDeletePluginConfig() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);
        given(pluginConfigRepository.existsByIdAndTenantId(CONFIG_ID, TENANT_A)).willReturn(true);

        mockMvc.perform(delete("/api/admin/plugin-configs/" + CONFIG_ID)
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A))
                .andExpect(status().isNoContent());

        verify(pluginConfigRepository).deleteByIdAndTenantId(CONFIG_ID, TENANT_A);
    }

    @Test
    @DisplayName("POST /api/admin/plugin-configs - unauthenticated request returns 401 Unauthorized")
    void testCreateUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/admin/plugin-configs")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pluginName", "key-auth"))))
                .andExpect(status().isUnauthorized());
    }
}
