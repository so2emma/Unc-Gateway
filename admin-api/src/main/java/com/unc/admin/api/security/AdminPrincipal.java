package com.unc.admin.api.security;

import java.io.Serializable;
import java.util.UUID;

public record AdminPrincipal(
        UUID id,
        String email,
        AdminRole role,
        UUID tenantScope
) implements Serializable {

    public boolean isAdmin() {
        return role == AdminRole.ADMIN;
    }
}
