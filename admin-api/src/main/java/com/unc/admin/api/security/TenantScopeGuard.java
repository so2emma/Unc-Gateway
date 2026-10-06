package com.unc.admin.api.security;

import com.unc.admin.api.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class TenantScopeGuard {

    private final ObjectProvider<HttpServletRequest> requestProvider;

    public TenantScopeGuard(ObjectProvider<HttpServletRequest> requestProvider) {
        this.requestProvider = requestProvider;
    }

    public UUID checkTenantScope() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header is required");
        }
        return checkTenantScope(request);
    }

    public UUID checkTenantScope(HttpServletRequest request) {
        String rawTenantId = request.getHeader("X-Tenant-Id");
        if (rawTenantId == null || rawTenantId.trim().isEmpty()) {
            rawTenantId = request.getParameter("tenant_id");
        }
        return checkTenantScope(rawTenantId);
    }

    public UUID checkTenantScope(String rawTenantId) {
        if (rawTenantId == null || rawTenantId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header is required");
        }

        UUID requestedTenantId = parseTenantId(rawTenantId);
        if (requestedTenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header must be a valid UUID");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminPrincipal principal) {
            checkTenantScope(principal, requestedTenantId);
        }

        TenantContext.setTenantId(requestedTenantId);
        return requestedTenantId;
    }

    public void checkTenantScope(AdminPrincipal principal, UUID requestedTenantId) {
        if (principal == null) {
            throw new AccessDeniedException("Unauthenticated");
        }
        if (principal.role() == AdminRole.ADMIN) {
            return;
        }
        if (principal.tenantScope() == null || !principal.tenantScope().equals(requestedTenantId)) {
            throw new AccessDeniedException("Cross-tenant access denied: caller tenant scope does not match requested tenant");
        }
    }

    public static UUID parseTenantId(String rawTenantId) {
        if (rawTenantId == null || rawTenantId.trim().isEmpty()) {
            return null;
        }
        String trimmed = rawTenantId.trim();
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(trimmed.getBytes(StandardCharsets.UTF_8));
        }
    }
}
