package com.unc.admin.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unc.admin.api.entity.ConsumerKeyEntity;
import com.unc.admin.api.repository.ConsumerKeyRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ConsumerKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConsumerRepository consumerRepository;

    @MockBean
    private ConsumerKeyRepository consumerKeyRepository;

    @MockBean
    private TenantRepository tenantRepository;

    private static final UUID TENANT_A = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final String API_KEY_A = "unc_live_sec_1234567890abcdef";
    private static final UUID CONSUMER_ID = UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380c33");
    private static final UUID KEY_ID = UUID.fromString("e4eebc99-9c0b-4ef8-bb6d-6bb9bd380e55");

    @Test
    @DisplayName("POST /api/admin/consumers/{id}/keys - returns 201 with the raw API key")
    void testIssueKeyReturnsRawKeyOnce() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_ID, TENANT_A)).willReturn(true);
        given(consumerKeyRepository.existsByKeyHash(any())).willReturn(false);
        given(consumerKeyRepository.save(any(ConsumerKeyEntity.class))).willAnswer(invocation -> {
            ConsumerKeyEntity toSave = invocation.getArgument(0);
            toSave.setId(KEY_ID);
            return toSave;
        });

        mockMvc.perform(post("/api/admin/consumers/" + CONSUMER_ID + "/keys")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(KEY_ID.toString()))
                .andExpect(jsonPath("$.consumerId").value(CONSUMER_ID.toString()))
                .andExpect(jsonPath("$.key").value(org.hamcrest.Matchers.startsWith("unc_key_")))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/admin/consumers/{id}/keys - a consumer of another tenant returns 404 Not Found")
    void testIssueKeyForeignConsumerReturns404() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_ID, TENANT_A)).willReturn(false);

        mockMvc.perform(post("/api/admin/consumers/" + CONSUMER_ID + "/keys")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        verify(consumerKeyRepository, never()).save(any(ConsumerKeyEntity.class));
    }

    @Test
    @DisplayName("GET /api/admin/consumers/{id}/keys - lists key metadata without the raw key")
    void testListKeysReturnsMetadataOnly() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_ID, TENANT_A)).willReturn(true);

        ConsumerKeyEntity stored = new ConsumerKeyEntity();
        stored.setId(KEY_ID);
        stored.setTenantId(TENANT_A);
        stored.setConsumerId(CONSUMER_ID);
        stored.setName("default");
        stored.setKeyPrefix("unc_key_abcd1234");
        stored.setKeyHash("a".repeat(64));
        stored.setStatus("ACTIVE");

        given(consumerKeyRepository.findByConsumerIdAndTenantId(CONSUMER_ID, TENANT_A)).willReturn(List.of(stored));

        mockMvc.perform(get("/api/admin/consumers/" + CONSUMER_ID + "/keys")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].keyHash").value("a".repeat(64)))
                .andExpect(jsonPath("$[0].keyPrefix").value("unc_key_abcd1234"))
                .andExpect(jsonPath("$[0].key").doesNotExist());

        verify(consumerKeyRepository).findByConsumerIdAndTenantId(CONSUMER_ID, TENANT_A);
    }

    @Test
    @DisplayName("DELETE /api/admin/consumers/{id}/keys/{keyId} - revokes the key and returns 204")
    void testRevokeKey() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_ID, TENANT_A)).willReturn(true);

        ConsumerKeyEntity stored = new ConsumerKeyEntity();
        stored.setId(KEY_ID);
        stored.setTenantId(TENANT_A);
        stored.setConsumerId(CONSUMER_ID);
        stored.setKeyPrefix("unc_key_abcd1234");
        stored.setKeyHash("a".repeat(64));
        stored.setStatus("ACTIVE");

        given(consumerKeyRepository.findByIdAndConsumerIdAndTenantId(KEY_ID, CONSUMER_ID, TENANT_A))
                .willReturn(Optional.of(stored));
        given(consumerKeyRepository.save(any(ConsumerKeyEntity.class))).willAnswer(i -> i.getArgument(0));

        mockMvc.perform(delete("/api/admin/consumers/" + CONSUMER_ID + "/keys/" + KEY_ID)
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A))
                .andExpect(status().isNoContent());

        verify(consumerKeyRepository).save(stored);
    }

    @Test
    @DisplayName("DELETE /api/admin/consumers/{id}/keys/{keyId} - a key of another tenant returns 404 Not Found")
    void testRevokeForeignKeyReturns404() throws Exception {
        given(tenantRepository.existsByIdAndApiKeyAndStatus(TENANT_A, API_KEY_A, "ACTIVE")).willReturn(true);
        given(consumerRepository.existsByIdAndTenantId(CONSUMER_ID, TENANT_A)).willReturn(true);
        given(consumerKeyRepository.findByIdAndConsumerIdAndTenantId(KEY_ID, CONSUMER_ID, TENANT_A))
                .willReturn(Optional.empty());

        mockMvc.perform(delete("/api/admin/consumers/" + CONSUMER_ID + "/keys/" + KEY_ID)
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .header("X-Api-Key", API_KEY_A))
                .andExpect(status().isNotFound());

        verify(consumerKeyRepository, never()).save(any(ConsumerKeyEntity.class));
    }

    @Test
    @DisplayName("POST /api/admin/consumers/{id}/keys - unauthenticated request returns 401 Unauthorized")
    void testIssueKeyUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/admin/consumers/" + CONSUMER_ID + "/keys")
                        .header("X-Tenant-Id", TENANT_A.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isUnauthorized());
    }
}
