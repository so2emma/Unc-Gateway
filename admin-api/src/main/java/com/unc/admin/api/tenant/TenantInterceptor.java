package com.unc.admin.api.tenant;

import com.unc.admin.api.entity.TenantEntity;
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
        if (!uri.startsWith("/api/admin")) {
            return true;
        }

        String rawTenantId = request.getHeader("X-Tenant-Id");
        if (rawTenantId == null || rawTenantId.trim().isEmpty()) {
            rawTenantId = request.getParameter("tenant_id");
        }

        if (rawTenantId == null || rawTenantId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header is required");
        }

        UUID tenantId;
        try {
            tenantId = UUID.fromString(rawTenantId.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header must be a valid UUID");
        }

        TenantContext.setTenantId(tenantId);

        if (!tenantRepository.existsById(tenantId)) {
            TenantEntity newTenant = new TenantEntity();
            newTenant.setId(tenantId);
            newTenant.setTenantId(tenantId);
            newTenant.setName("Tenant-" + tenantId);
            tenantRepository.saveAndFlush(newTenant);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
