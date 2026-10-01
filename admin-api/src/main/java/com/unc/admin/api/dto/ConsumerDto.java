package com.unc.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consumer payload. Accepts {@code name} as an alias for {@code username} so that both the operator
 * dashboard and the developer-portal self-serve signup form can post their natural field name.
 */
public class ConsumerDto {

    private UUID id;
    private UUID tenantId;

    @JsonAlias({"name", "username"})
    @JsonProperty("username")
    private String username;

    private String customId;
    private String email;
    private String organization;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public ConsumerDto() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @JsonProperty("name")
    public String getName() {
        return username;
    }

    @JsonProperty("name")
    public void setName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.username = name;
        }
    }

    public String getCustomId() {
        return customId;
    }

    public void setCustomId(String customId) {
        this.customId = customId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
