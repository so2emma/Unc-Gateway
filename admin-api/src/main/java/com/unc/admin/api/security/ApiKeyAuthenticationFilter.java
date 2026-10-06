package com.unc.admin.api.security;

import com.unc.admin.api.entity.AdminApiKeyEntity;
import com.unc.admin.api.repository.AdminApiKeyRepository;
import com.unc.admin.api.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String ADMIN_API_KEY_HEADER = "X-Admin-Api-Key";

    private final AdminApiKeyRepository adminApiKeyRepository;
    private final TenantRepository tenantRepository;

    public ApiKeyAuthenticationFilter(
            org.springframework.beans.factory.ObjectProvider<AdminApiKeyRepository> adminApiKeyRepositoryProvider,
            org.springframework.beans.factory.ObjectProvider<TenantRepository> tenantRepositoryProvider) {
        this.adminApiKeyRepository = adminApiKeyRepositoryProvider.getIfAvailable();
        this.tenantRepository = tenantRepositoryProvider.getIfAvailable();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // If already authenticated by JwtAuthenticationFilter, proceed
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String adminApiKey = request.getHeader(ADMIN_API_KEY_HEADER);
        if (adminApiKey != null && !adminApiKey.isBlank()) {
            if (adminApiKeyRepository != null) {
                String hash = RefreshTokenService.hashToken(adminApiKey);
                var optKey = adminApiKeyRepository.findByKeyHash(hash);
                if (optKey.isPresent()) {
                    AdminApiKeyEntity keyEntity = optKey.get();
                    boolean isExpired = keyEntity.getExpiresAt() != null
                            && keyEntity.getExpiresAt().isBefore(OffsetDateTime.now());
                    if (!keyEntity.isRevoked() && !isExpired) {
                        AdminPrincipal principal = new AdminPrincipal(
                                keyEntity.getId(),
                                keyEntity.getLabel(),
                                keyEntity.getRole(),
                                keyEntity.getTenantScope()
                        );
                        var auth = new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + keyEntity.getRole().name()))
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        filterChain.doFilter(request, response);
                        return;
                    }
                }
            }

            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid, revoked, or expired API key\"}");
            return;
        }

        // Support legacy X-Api-Key authentication for backwards-compatibility
        String apiKey = request.getHeader("X-Api-Key");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = request.getParameter("api_key");
        }
        String rawTenantId = request.getHeader("X-Tenant-Id");
        if (rawTenantId == null || rawTenantId.isBlank()) {
            rawTenantId = request.getParameter("tenant_id");
        }

        if (apiKey != null && !apiKey.isBlank() && rawTenantId != null && !rawTenantId.isBlank() && tenantRepository != null) {
            UUID tenantId = TenantScopeGuard.parseTenantId(rawTenantId);
            if (tenantId != null && tenantRepository.existsByIdAndApiKeyAndStatus(tenantId, apiKey.trim(), "ACTIVE")) {
                AdminPrincipal principal = new AdminPrincipal(
                        tenantId,
                        "legacy@" + tenantId,
                        AdminRole.OPERATOR,
                        tenantId
                );
                var auth = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
