package com.unc.admin.api.security;

import com.unc.admin.api.entity.AdminApiKeyEntity;
import com.unc.admin.api.repository.AdminApiKeyRepository;
import com.unc.admin.api.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("Phase 23: ApiKeyAuthenticationFilter Unit Tests")
class ApiKeyAuthenticationFilterTest {

    private AdminApiKeyRepository adminApiKeyRepository;
    private TenantRepository tenantRepository;
    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        adminApiKeyRepository = mock(AdminApiKeyRepository.class);
        tenantRepository = mock(TenantRepository.class);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("adminApiKeyRepository", adminApiKeyRepository);
        beanFactory.addBean("tenantRepository", tenantRepository);

        filter = new ApiKeyAuthenticationFilter(
                beanFactory.getBeanProvider(AdminApiKeyRepository.class),
                beanFactory.getBeanProvider(TenantRepository.class)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Valid X-Admin-Api-Key populates SecurityContext with correct AdminPrincipal")
    void testValidAdminApiKeyPopulatesSecurityContext() throws Exception {
        String rawKey = "unc_adm_test1234567890abcdef1234567890abcdef";
        String hash = RefreshTokenService.hashToken(rawKey);

        UUID keyId = UUID.randomUUID();
        UUID tenantScope = UUID.randomUUID();
        AdminApiKeyEntity entity = new AdminApiKeyEntity(
                keyId, hash, "ci-key", AdminRole.OPERATOR, tenantScope, OffsetDateTime.now().plusDays(30), false
        );

        given(adminApiKeyRepository.findByKeyHash(hash)).willReturn(Optional.of(entity));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Admin-Api-Key", rawKey);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        AdminPrincipal principal = (AdminPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.id()).isEqualTo(keyId);
        assertThat(principal.role()).isEqualTo(AdminRole.OPERATOR);
        assertThat(principal.tenantScope()).isEqualTo(tenantScope);
    }

    @Test
    @DisplayName("Missing X-Admin-Api-Key leaves SecurityContext empty without setting error status")
    void testMissingApiKeyLeavesSecurityContextEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Revoked X-Admin-Api-Key results in HTTP 401 and empty SecurityContext")
    void testRevokedApiKeyRejectsWith401() throws Exception {
        String rawKey = "unc_adm_revoked_key";
        String hash = RefreshTokenService.hashToken(rawKey);

        AdminApiKeyEntity revokedEntity = new AdminApiKeyEntity(
                UUID.randomUUID(), hash, "revoked", AdminRole.ADMIN, null, OffsetDateTime.now().plusDays(1), true
        );
        given(adminApiKeyRepository.findByKeyHash(hash)).willReturn(Optional.of(revokedEntity));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Admin-Api-Key", rawKey);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Expired X-Admin-Api-Key results in HTTP 401 and empty SecurityContext")
    void testExpiredApiKeyRejectsWith401() throws Exception {
        String rawKey = "unc_adm_expired_key";
        String hash = RefreshTokenService.hashToken(rawKey);

        AdminApiKeyEntity expiredEntity = new AdminApiKeyEntity(
                UUID.randomUUID(), hash, "expired", AdminRole.OPERATOR, UUID.randomUUID(), OffsetDateTime.now().minusHours(1), false
        );
        given(adminApiKeyRepository.findByKeyHash(hash)).willReturn(Optional.of(expiredEntity));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Admin-Api-Key", rawKey);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Unknown X-Admin-Api-Key results in HTTP 401 and empty SecurityContext")
    void testUnknownApiKeyRejectsWith401() throws Exception {
        String rawKey = "unc_adm_unknown_key";
        String hash = RefreshTokenService.hashToken(rawKey);

        given(adminApiKeyRepository.findByKeyHash(hash)).willReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Admin-Api-Key", rawKey);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
