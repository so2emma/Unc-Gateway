package com.unc.admin.api.tenant;

import com.unc.admin.api.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    private final TenantRepository tenantRepository;

    public TenantInterceptor(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (!uri.startsWith("/api/admin")) {
            return true;
        }

        // Allow public registration endpoint POST /api/admin/tenants
        if ("/api/admin/tenants".equals(uri) && "POST".equalsIgnoreCase(method)) {
            return true;
        }

        String rawTenantId = request.getHeader("X-Tenant-Id");
        if (rawTenantId == null || rawTenantId.trim().isEmpty()) {
            rawTenantId = request.getParameter("tenant_id");
        }

        if (rawTenantId == null || rawTenantId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header is required");
        }

        String apiKey = request.getHeader("X-Api-Key");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = request.getParameter("api_key");
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Api-Key header is required for authentication");
        }

        UUID tenantId;
        try {
            tenantId = UUID.fromString(rawTenantId.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header must be a valid UUID");
        }

        boolean validAuth = tenantRepository.existsByIdAndApiKeyAndStatus(tenantId, apiKey.trim(), "ACTIVE");
        if (!validAuth) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid tenant credentials or inactive tenant status");
        }

        TenantContext.setTenantId(tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
