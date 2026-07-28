package com.unc.admin.api.tenant;

import com.unc.admin.api.entity.TenantEntity;
import com.unc.admin.api.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

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

        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = request.getParameter("tenant_id");
        }

        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header is required");
        }

        tenantId = tenantId.trim();
        TenantContext.setTenantId(tenantId);

        if (!tenantRepository.existsById(tenantId)) {
            tenantRepository.save(new TenantEntity(tenantId, tenantId));
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
