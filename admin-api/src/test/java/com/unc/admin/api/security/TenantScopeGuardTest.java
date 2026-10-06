package com.unc.admin.api.security;

import com.unc.admin.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Phase 23: TenantScopeGuard Unit Tests")
class TenantScopeGuardTest {

    private static final String TENANT_A_STR = "tenant-a";
    private static final String TENANT_B_STR = "tenant-b";

    private static final UUID TENANT_A = TenantScopeGuard.parseTenantId(TENANT_A_STR);
    private static final UUID TENANT_B = TenantScopeGuard.parseTenantId(TENANT_B_STR);

    private TenantScopeGuard guard;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        guard = new TenantScopeGuard(beanFactory.getBeanProvider(jakarta.servlet.http.HttpServletRequest.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("OPERATOR principal with tenantScope = tenant-a allowed for X-Tenant-Id: tenant-a")
    void testOperatorAllowedForMatchingTenantScope() {
        AdminPrincipal operator = new AdminPrincipal(UUID.randomUUID(), "op@unc.dev", AdminRole.OPERATOR, TENANT_A);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(operator, null, List.of())
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", TENANT_A_STR);

        UUID resolved = guard.checkTenantScope(request);

        assertThat(resolved).isEqualTo(TENANT_A);
        assertThat(TenantContext.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    @DisplayName("OPERATOR principal with tenantScope = tenant-a rejected with AccessDeniedException for X-Tenant-Id: tenant-b")
    void testOperatorRejectedForMismatchedTenantScope() {
        AdminPrincipal operator = new AdminPrincipal(UUID.randomUUID(), "op@unc.dev", AdminRole.OPERATOR, TENANT_A);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(operator, null, List.of())
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", TENANT_B_STR);

        assertThatThrownBy(() -> guard.checkTenantScope(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cross-tenant access denied");
    }

    @Test
    @DisplayName("VIEWER principal with tenantScope = tenant-a rejected with AccessDeniedException for X-Tenant-Id: tenant-b")
    void testViewerRejectedForMismatchedTenantScope() {
        AdminPrincipal viewer = new AdminPrincipal(UUID.randomUUID(), "viewer@unc.dev", AdminRole.VIEWER, TENANT_A);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(viewer, null, List.of())
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", TENANT_B_STR);

        assertThatThrownBy(() -> guard.checkTenantScope(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cross-tenant access denied");
    }

    @Test
    @DisplayName("ADMIN principal is allowed for any tenant ID")
    void testAdminAllowedForAnyTenantId() {
        AdminPrincipal admin = new AdminPrincipal(UUID.randomUUID(), "admin@unc.dev", AdminRole.ADMIN, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of())
        );

        MockHttpServletRequest requestA = new MockHttpServletRequest();
        requestA.addHeader("X-Tenant-Id", TENANT_A_STR);
        assertThat(guard.checkTenantScope(requestA)).isEqualTo(TENANT_A);
        assertThat(TenantContext.getTenantId()).isEqualTo(TENANT_A);

        MockHttpServletRequest requestB = new MockHttpServletRequest();
        requestB.addHeader("X-Tenant-Id", TENANT_B_STR);
        assertThat(guard.checkTenantScope(requestB)).isEqualTo(TENANT_B);
        assertThat(TenantContext.getTenantId()).isEqualTo(TENANT_B);
    }

    @Test
    @DisplayName("Missing X-Tenant-Id header throws ResponseStatusException with 400 Bad Request")
    void testMissingTenantHeaderThrowsBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatThrownBy(() -> guard.checkTenantScope(request))
                .isInstanceOf(ResponseStatusException.class);
    }
}
