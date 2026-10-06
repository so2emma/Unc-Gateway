package com.unc.admin.api.dto;

import com.unc.admin.api.security.AdminRole;

import java.time.OffsetDateTime;

public class CreateAdminApiKeyRequest {

    private String label;
    private AdminRole role;
    private String tenantScope;
    private OffsetDateTime expiresAt;

    public CreateAdminApiKeyRequest() {
    }

    public CreateAdminApiKeyRequest(String label, AdminRole role, String tenantScope) {
        this.label = label;
        this.role = role;
        this.tenantScope = tenantScope;
    }

    public CreateAdminApiKeyRequest(String label, AdminRole role, String tenantScope, OffsetDateTime expiresAt) {
        this.label = label;
        this.role = role;
        this.tenantScope = tenantScope;
        this.expiresAt = expiresAt;
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
}
