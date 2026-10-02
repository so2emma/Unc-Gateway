package com.unc.admin.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unc.admin.api.entity.ConsumerEntity;
import com.unc.admin.api.repository.ConsumerRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ConsumerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConsumerRepository consumerRepository;

    @MockBean
    private TenantRepository tenantRepository;

    private static final UUID TENANT_A = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final String API_KEY_A = "unc_live_sec_1234567890abcdef";
    private static final UUID CONSUMER_ID = UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380c33");

    @Test
    @DisplayName("POST /api/admin/consumers - missing X-Tenant-Id header returns 400 Bad Request")
    void testCreateConsumerMissingTenantHeader() throws Exception {
        mockMvc.perform(post("/api/admin/consumers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "acme-corp"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/admin/consumers - missing X-Api-Key header returns 401 Unauthorized")
    void testCreateConsumerMissingApiKey() throws Exception {
        mockMvc.perform(post("/api/admin/consumers")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "acme-corp"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/admin/consumers - valid request returns 201 with the new consumer id")
    void testCreateConsumerSuccess() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);
        given(consumerRepository.existsByUsernameAndTenantId("acme-corp", TENANT_A)).willReturn(false);
        given(consumerRepository.save(any(ConsumerEntity.class))).willAnswer(invocation -> {
            ConsumerEntity toSave = invocation.getArgument(0);
            toSave.setId(CONSUMER_ID);
            return toSave;
        });

        mockMvc.perform(post("/api/admin/consumers")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "acme-corp",
                                "email", "dev@acme.example",
                                "organization", "Acme Corp"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CONSUMER_ID.toString()))
                .andExpect(jsonPath("$.name").value("acme-corp"))
                .andExpect(jsonPath("$.username").value("acme-corp"))
                .andExpect(jsonPath("$.email").value("dev@acme.example"))
                .andExpect(jsonPath("$.organization").value("Acme Corp"))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A.toString()));
    }

    @Test
    @DisplayName("POST /api/admin/consumers - a payload without a name returns 400 Bad Request")
    void testCreateConsumerWithoutNameRejected() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);

        mockMvc.perform(post("/api/admin/consumers")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dev@acme.example\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/admin/consumers - returns only the authenticated tenant's consumers")
    void testListConsumersTenantScoped() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);

        ConsumerEntity consumer = new ConsumerEntity();
        consumer.setId(CONSUMER_ID);
        consumer.setTenantId(TENANT_A);
        consumer.setUsername("acme-corp");

        given(consumerRepository.findByTenantId(TENANT_A)).willReturn(List.of(consumer));

        mockMvc.perform(get("/api/admin/consumers")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(CONSUMER_ID.toString()))
                .andExpect(jsonPath("$[0].tenantId").value(TENANT_A.toString()));

        verify(consumerRepository).findByTenantId(TENANT_A);
    }

    @Test
    @DisplayName("GET /api/admin/consumers/{id} - a consumer of another tenant returns 404 Not Found")
    void testGetForeignConsumerReturns404() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);
        given(consumerRepository.findByIdAndTenantId(CONSUMER_ID, TENANT_A)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/consumers/" + CONSUMER_ID)
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/admin/consumers/{id} - updates a consumer owned by the caller's tenant")
    void testUpdateConsumer() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);

        ConsumerEntity existing = new ConsumerEntity();
        existing.setId(CONSUMER_ID);
        existing.setTenantId(TENANT_A);
        existing.setUsername("acme-corp");

        given(consumerRepository.findByIdAndTenantId(CONSUMER_ID, TENANT_A)).willReturn(Optional.of(existing));
        given(consumerRepository.existsByUsernameAndTenantId("acme-renamed", TENANT_A)).willReturn(false);
        given(consumerRepository.save(any(ConsumerEntity.class))).willAnswer(i -> i.getArgument(0));

        mockMvc.perform(put("/api/admin/consumers/" + CONSUMER_ID)
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "acme-renamed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("acme-renamed"));
    }

    @Test
    @DisplayName("DELETE /api/admin/consumers/{id} - deletes scoped by tenant_id")
    void testDeleteConsumer() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_ID, TENANT_A)).willReturn(true);

        mockMvc.perform(delete("/api/admin/consumers/" + CONSUMER_ID)
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A))
                .andExpect(status().isNoContent());

        verify(consumerRepository).deleteByIdAndTenantId(CONSUMER_ID, TENANT_A);
    }
}
