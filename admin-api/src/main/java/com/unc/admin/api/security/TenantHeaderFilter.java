package com.unc.admin.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (uri.startsWith("/api/admin")) {
            // Bypass auth endpoints
            if (uri.startsWith("/api/admin/auth")) {
                filterChain.doFilter(request, response);
                return;
            }
            // Bypass public tenant registration
            if ("/api/admin/tenants".equals(uri) && "POST".equalsIgnoreCase(method)) {
                filterChain.doFilter(request, response);
                return;
            }

            String tenantId = request.getHeader("X-Tenant-Id");
            if (tenantId == null || tenantId.trim().isEmpty()) {
                tenantId = request.getParameter("tenant_id");
            }

            if (tenantId == null || tenantId.trim().isEmpty()) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"Bad Request\",\"message\":\"X-Tenant-Id header is required\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
