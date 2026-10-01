package com.unc.gateway.core.routing;

import com.unc.gateway.core.cache.RouteCache;
import com.unc.gateway.core.cache.RouteEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicRouteResolverTest {

    private RouteCache routeCache;
    private DynamicRouteResolver resolver;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        routeCache = new RouteCache();
        resolver = new DynamicRouteResolver(routeCache);
        tenantId = UUID.randomUUID();
    }

    @Test
    @DisplayName("given a request path present in RouteCache, verifies it resolves to the correct upstream target")
    void testResolvePresentRoute() {
        RouteEntry route = new RouteEntry("/demo", tenantId, "http://mock-upstream:8080");
        routeCache.bulkReplace(List.of(route));

        // Synchronous resolution returns upstream target
        String target = resolver.resolve("/demo");
        assertThat(target).isEqualTo("http://mock-upstream:8080");

        // With tenantId
        String targetWithTenant = resolver.resolve("/demo", tenantId);
        assertThat(targetWithTenant).isEqualTo("http://mock-upstream:8080");

        // Reactive exchange resolution
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/demo?ping=pong").build()
        );

        StepVerifier.create(resolver.resolveTarget(exchange))
                .expectNext("http://mock-upstream:8080?ping=pong")
                .verifyComplete();
    }

    @Test
    @DisplayName("given a path absent from the cache, verifies it returns 404 Not Found instead of forwarding")
    void testResolveAbsentRouteReturns404() {
        RouteEntry route = new RouteEntry("/demo", tenantId, "http://mock-upstream:8080");
        routeCache.bulkReplace(List.of(route));

        // Synchronous call throws ResponseStatusException with 404
        assertThatThrownBy(() -> resolver.resolve("/nonexistent"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> resolver.resolve("/demo", UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // Reactive exchange emits 404 error
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/unknown/path").build()
        );

        StepVerifier.create(resolver.resolveTarget(exchange))
                .expectErrorMatches(e -> e instanceof ResponseStatusException &&
                        ((ResponseStatusException) e).getStatusCode() == HttpStatus.NOT_FOUND)
                .verify();
    }

    @Test
    @DisplayName("resolves sub-paths with stripPath true and false")
    void testSubPathAndStripPath() {
        RouteEntry stripped = new RouteEntry(UUID.randomUUID(), UUID.randomUUID(), tenantId, "/api", "http://backend:8080", true);
        RouteEntry nonStripped = new RouteEntry(UUID.randomUUID(), UUID.randomUUID(), tenantId, "/raw", "http://backend:8080", false);
        routeCache.bulkReplace(List.of(stripped, nonStripped));

        MockServerWebExchange strippedExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/42").build()
        );
        StepVerifier.create(resolver.resolveTarget(strippedExchange))
                .expectNext("http://backend:8080/users/42")
                .verifyComplete();

        MockServerWebExchange nonStrippedExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/raw/items").build()
        );
        StepVerifier.create(resolver.resolveTarget(nonStrippedExchange))
                .expectNext("http://backend:8080/raw/items")
                .verifyComplete();
    }

    @Test
    @DisplayName("resolves tenant from X-Tenant-Id header")
    void testResolveWithTenantHeader() {
        UUID otherTenant = UUID.randomUUID();
        RouteEntry routeA = new RouteEntry("/shared", tenantId, "http://tenant-a-upstream:8080");
        RouteEntry routeB = new RouteEntry("/shared", otherTenant, "http://tenant-b-upstream:8080");
        routeCache.bulkReplace(List.of(routeA, routeB));

        MockServerWebExchange exchangeA = MockServerWebExchange.from(
                MockServerHttpRequest.get("/shared")
                        .header("X-Tenant-Id", tenantId.toString())
                        .build()
        );
        StepVerifier.create(resolver.resolveTarget(exchangeA))
                .expectNext("http://tenant-a-upstream:8080")
                .verifyComplete();

        MockServerWebExchange exchangeB = MockServerWebExchange.from(
                MockServerHttpRequest.get("/shared")
                        .header("X-Tenant-Id", otherTenant.toString())
                        .build()
        );
        StepVerifier.create(resolver.resolveTarget(exchangeB))
                .expectNext("http://tenant-b-upstream:8080")
                .verifyComplete();
    }
}
