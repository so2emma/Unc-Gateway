package com.unc.admin.api.dto;

import com.unc.admin.api.security.AdminRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AdminApiKeyResponse {

    private UUID id;
    private String key;
    private String label;
    private AdminRole role;
    private String tenantScope;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;

    public AdminApiKeyResponse() {
    }

    public AdminApiKeyResponse(UUID id, String key, String label, AdminRole role, String tenantScope, OffsetDateTime expiresAt, OffsetDateTime createdAt) {
        this.id = id;
        this.key = key;
        this.label = label;
        this.role = role;
        this.tenantScope = tenantScope;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public AdminRole getRole() {
        return role;
    }

    public void setRole(AdminRole role) {
        this.role = role;
    }

    public String getTenantScope() {
        return tenantScope;
    }

    public void setTenantScope(String tenantScope) {
        this.tenantScope = tenantScope;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
