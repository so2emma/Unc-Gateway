package com.unc.admin.api.isolation;

import com.unc.admin.api.security.AdminPrincipal;
import com.unc.admin.api.security.AdminRole;
import com.unc.admin.api.security.TenantScopeGuard;
import com.unc.admin.api.tenant.TenantContext;
import net.jqwik.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class CrossTenantAccessPropertyTest {

    enum EndpointVariant {
        GET, POST, PUT, DELETE
    }

    static class RepositoryTracker {
        final AtomicInteger readCount = new AtomicInteger();
        final AtomicInteger writeCount = new AtomicInteger();

        void recordRead() {
            readCount.incrementAndGet();
        }

        void recordWrite() {
            writeCount.incrementAndGet();
        }

        void reset() {
            readCount.set(0);
            writeCount.set(0);
        }
    }

    private final TenantScopeGuard guard;
    private final RepositoryTracker tracker = new RepositoryTracker();

    CrossTenantAccessPropertyTest() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        this.guard = new TenantScopeGuard(beanFactory.getBeanProvider(jakarta.servlet.http.HttpServletRequest.class));
    }

    @Property(tries = 150, generation = GenerationMode.RANDOMIZED)
    @Label("CROSS_TENANT_ACCESS: Non-ADMIN principal with tenantScope T1 calling endpoint with X-Tenant-Id T2 (T2 != T1) returns HTTP 403 and performs zero database reads or writes")
    boolean crossTenantAccessBlockedWithZeroDbAccess(
            @ForAll("nonAdminRoles") AdminRole role,
            @ForAll("tenantIdPair") List<UUID> tenantPair,
            @ForAll("endpointVariants") EndpointVariant variant
    ) {
        UUID t1 = tenantPair.get(0);
        UUID t2 = tenantPair.get(1);

        tracker.reset();
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        AdminPrincipal principal = new AdminPrincipal(UUID.randomUUID(), "test-caller@unc.local", role, t1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(variant.name());
        request.addHeader("X-Tenant-Id", t2.toString());

        int statusCode;
        try {
            // Emulate controller handler entry sequence
            UUID resolvedTenantId = guard.checkTenantScope(request);

            // If guard passes, repository operations would execute:
            switch (variant) {
                case GET -> {
                    tracker.recordRead();
                    statusCode = 200;
                }
                case POST -> {
                    tracker.recordWrite();
                    statusCode = 201;
                }
                case PUT -> {
                    tracker.recordRead();
                    tracker.recordWrite();
                    statusCode = 200;
                }
                case DELETE -> {
                    tracker.recordRead();
                    tracker.recordWrite();
                    statusCode = 204;
                }
                default -> throw new IllegalStateException("Unexpected variant: " + variant);
            }
        } catch (AccessDeniedException ex) {
            statusCode = 403;
        } catch (Exception ex) {
            statusCode = 500;
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }

        boolean returnedForbidden = (statusCode == 403);
        boolean zeroDbReads = (tracker.readCount.get() == 0);
        boolean zeroDbWrites = (tracker.writeCount.get() == 0);

        return returnedForbidden && zeroDbReads && zeroDbWrites;
    }

    @Property(tries = 150, generation = GenerationMode.RANDOMIZED)
    @Label("CROSS_TENANT_ACCESS: ADMIN principal calling endpoint with any tenant T2 succeeds and executes requested operation")
    boolean adminAllowedForAnyTenant(
            @ForAll("anyTenantId") UUID t2,
            @ForAll("endpointVariants") EndpointVariant variant
    ) {
        tracker.reset();
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        AdminPrincipal admin = new AdminPrincipal(UUID.randomUUID(), "admin@unc.local", AdminRole.ADMIN, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of())
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(variant.name());
        request.addHeader("X-Tenant-Id", t2.toString());

        int statusCode;
        try {
            guard.checkTenantScope(request);
            switch (variant) {
                case GET -> {
                    tracker.recordRead();
                    statusCode = 200;
                }
                case POST -> {
                    tracker.recordWrite();
                    statusCode = 201;
                }
                case PUT -> {
                    tracker.recordRead();
                    tracker.recordWrite();
                    statusCode = 200;
                }
                case DELETE -> {
                    tracker.recordRead();
                    tracker.recordWrite();
                    statusCode = 204;
                }
                default -> throw new IllegalStateException("Unexpected variant: " + variant);
            }
        } catch (AccessDeniedException ex) {
            statusCode = 403;
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }

        return statusCode != 403 && (tracker.readCount.get() > 0 || tracker.writeCount.get() > 0);
    }

    @Provide
    Arbitrary<AdminRole> nonAdminRoles() {
        return Arbitraries.of(AdminRole.OPERATOR, AdminRole.VIEWER);
    }

    @Provide
    Arbitrary<List<UUID>> tenantIdPair() {
        return Arbitraries.randomValue(random -> UUID.randomUUID())
                .list()
                .ofSize(2)
                .filter(list -> !list.get(0).equals(list.get(1)));
    }

    @Provide
    Arbitrary<UUID> anyTenantId() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<EndpointVariant> endpointVariants() {
        return Arbitraries.of(EndpointVariant.GET, EndpointVariant.POST, EndpointVariant.PUT, EndpointVariant.DELETE);
    }
}
