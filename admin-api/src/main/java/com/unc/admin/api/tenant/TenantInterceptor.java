package com.unc.admin.api.tenant;

import com.unc.admin.api.repository.TenantRepository;
import com.unc.admin.api.security.AdminPrincipal;
import com.unc.admin.api.security.AdminRole;
import com.unc.admin.api.security.TenantScopeGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
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

        // Allow auth endpoints
        if (uri.startsWith("/api/admin/auth")) {
            return true;
        }

        // If request is authenticated via Spring Security (e.g. JWT or X-Admin-Api-Key)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AdminPrincipal) {
            String rawTenantId = request.getHeader("X-Tenant-Id");
            if (rawTenantId == null || rawTenantId.trim().isEmpty()) {
                rawTenantId = request.getParameter("tenant_id");
            }
            if (rawTenantId != null && !rawTenantId.trim().isEmpty()) {
                UUID tenantId = TenantScopeGuard.parseTenantId(rawTenantId.trim());
                TenantContext.setTenantId(tenantId);
            }
            return true;
        }

        // Legacy / fallback auth via X-Api-Key
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

        UUID tenantId = TenantScopeGuard.parseTenantId(rawTenantId.trim());
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header must be a valid UUID");
        }

        boolean validAuth = tenantRepository.existsByIdAndApiKeyAndStatus(tenantId, apiKey.trim(), "ACTIVE");
        if (!validAuth) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid tenant credentials or inactive tenant status");
        }

        AdminPrincipal principal = new AdminPrincipal(tenantId, "legacy@" + tenantId, AdminRole.OPERATOR, tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")))
        );

        TenantContext.setTenantId(tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
