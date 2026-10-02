package com.unc.gateway.core.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RouteCacheTest {

    private RouteCache routeCache;
    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setUp() {
        routeCache = new RouteCache();
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
    }

    @Test
    @DisplayName("bulkReplace followed by lookup returns expected RouteEntry for known path/tenant and empty for unknown")
    void testBulkReplaceAndLookupKnownAndUnknown() {
        RouteEntry entry1 = new RouteEntry("/demo", tenantA, "http://upstream-a:8080");
        RouteEntry entry2 = new RouteEntry("/orders", tenantA, "http://orders-svc:8080");
        RouteEntry entry3 = new RouteEntry("/demo", tenantB, "http://upstream-b:8080");

        routeCache.bulkReplace(List.of(entry1, entry2, entry3));

        // Known path and tenant returns expected entry
        Optional<RouteEntry> result1 = routeCache.lookup("/demo", tenantA);
        assertThat(result1).isPresent();
        assertThat(result1.get().upstreamUrl()).isEqualTo("http://upstream-a:8080");
        assertThat(result1.get().path()).isEqualTo("/demo");

        Optional<RouteEntry> result3 = routeCache.lookup("/demo", tenantB);
        assertThat(result3).isPresent();
        assertThat(result3.get().upstreamUrl()).isEqualTo("http://upstream-b:8080");

        // Unknown path returns empty
        Optional<RouteEntry> unknownPath = routeCache.lookup("/nonexistent", tenantA);
        assertThat(unknownPath).isEmpty();

        // Unknown tenant returns empty
        UUID unknownTenant = UUID.randomUUID();
        Optional<RouteEntry> unknownTenantResult = routeCache.lookup("/demo", unknownTenant);
        assertThat(unknownTenantResult).isEmpty();
    }

    @Test
    @DisplayName("lookup matches sub-paths using prefix matching and longest prefix match")
    void testPrefixMatchingAndLongestMatch() {
        RouteEntry baseRoute = new RouteEntry("/api", tenantA, "http://api-service:8080");
        RouteEntry usersRoute = new RouteEntry("/api/users", tenantA, "http://users-service:8080");

        routeCache.bulkReplace(List.of(baseRoute, usersRoute));

        // Matches base route prefix
        Optional<RouteEntry> res1 = routeCache.lookup("/api/items", tenantA);
        assertThat(res1).isPresent();
        assertThat(res1.get().upstreamUrl()).isEqualTo("http://api-service:8080");

        // Longest prefix match selects users service
        Optional<RouteEntry> res2 = routeCache.lookup("/api/users/123", tenantA);
        assertThat(res2).isPresent();
        assertThat(res2.get().upstreamUrl()).isEqualTo("http://users-service:8080");

        // Word boundary: /apikey does not match /api
        Optional<RouteEntry> res3 = routeCache.lookup("/apikey", tenantA);
        assertThat(res3).isEmpty();
    }

    @Test
    @DisplayName("lookup by path without tenant finds matching entry")
    void testLookupWithoutTenant() {
        RouteEntry entry = new RouteEntry("/public", tenantA, "http://public-service:8080");
        routeCache.bulkReplace(List.of(entry));

        Optional<RouteEntry> found = routeCache.lookup("/public");
        assertThat(found).isPresent();
        assertThat(found.get().upstreamUrl()).isEqualTo("http://public-service:8080");
    }

    @Test
    @DisplayName("evict by routeId removes entry from cache")
    void testEvictByRouteId() {
        UUID routeId = UUID.randomUUID();
        RouteEntry entry = new RouteEntry(routeId, UUID.randomUUID(), tenantA, "/service", "http://svc:8080");
        routeCache.put(entry);

        assertThat(routeCache.lookup("/service", tenantA)).isPresent();
        assertThat(routeCache.size()).isEqualTo(1);

        routeCache.evict(routeId);

        assertThat(routeCache.lookup("/service", tenantA)).isEmpty();
        assertThat(routeCache.size()).isEqualTo(0);
    }
}
