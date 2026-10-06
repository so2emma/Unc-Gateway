package com.unc.admin.api.security;

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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phase 23: JwtAuthenticationFilter Unit Tests")
class JwtAuthenticationFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final Instant baseTime = Instant.parse("2026-10-05T12:00:00Z");
    private final Clock clock = Clock.fixed(baseTime, ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(SECRET, 900, clock);
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("jwtService", jwtService);
        filter = new JwtAuthenticationFilter(beanFactory.getBeanProvider(JwtService.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Valid bearer token populates SecurityContext with correct AdminPrincipal")
    void testValidBearerTokenPopulatesSecurityContext() throws Exception {
        UUID id = UUID.randomUUID();
        UUID tenantScope = UUID.randomUUID();
        AdminPrincipal principal = new AdminPrincipal(id, "operator@unc.dev", AdminRole.OPERATOR, tenantScope);
        String token = jwtService.issueToken(principal);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(principal);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_OPERATOR");
    }

    @Test
    @DisplayName("Request with no token does not populate SecurityContext")
    void testNoTokenLeavesSecurityContextEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Malformed bearer token results in HTTP 401 and no SecurityContext population")
    void testMalformedTokenRejectsWith401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-junk-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Expired bearer token results in HTTP 401 and no SecurityContext population")
    void testExpiredTokenRejectsWith401() throws Exception {
        // Issue token 1000s in the past
        Clock pastClock = Clock.fixed(baseTime.minusSeconds(1000), ZoneOffset.UTC);
        JwtService pastService = new JwtService(SECRET, 900, pastClock);
        String expiredToken = pastService.issueToken(
                new AdminPrincipal(UUID.randomUUID(), "exp@unc.dev", AdminRole.VIEWER, UUID.randomUUID())
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + expiredToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
