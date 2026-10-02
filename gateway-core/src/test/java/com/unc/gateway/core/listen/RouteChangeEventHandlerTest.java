package com.unc.gateway.core.listen;

import com.unc.gateway.core.cache.RouteCache;
import com.unc.gateway.core.cache.RouteCacheLoader;
import com.unc.gateway.core.cache.RouteEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Phase 12: RouteChangeEventHandler Unit Tests")
class RouteChangeEventHandlerTest {

    private RouteCacheLoader routeCacheLoader;
    private RouteCache routeCache;
    private RouteChangeEventHandler handler;

    @BeforeEach
    void setUp() {
        routeCacheLoader = mock(RouteCacheLoader.class);
        routeCache = mock(RouteCache.class);
        handler = new RouteChangeEventHandler(routeCacheLoader, routeCache);
    }

    @Test
    @DisplayName("INSERT on routes triggers single-row reload and cache update for the correct cache key")
    void testInsertRouteTriggersSingleRowReloadAndCacheUpdate() {
        UUID routeId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        RouteEntry freshEntry = new RouteEntry(routeId, serviceId, tenantId, "/demo", "http://mock-upstream:8080");
        when(routeCacheLoader.loadRouteById(routeId)).thenReturn(Mono.just(freshEntry));

        RouteChangeEvent event = new RouteChangeEvent("INSERT", "routes", routeId, tenantId);

        StepVerifier.create(handler.handleEvent(event))
                .verifyComplete();

        verify(routeCacheLoader, times(1)).loadRouteById(routeId);
        verify(routeCache, times(1)).evict(routeId);
        verify(routeCache, times(1)).put(freshEntry);
    }

    @Test
    @DisplayName("UPDATE on routes triggers eviction of old key, single-row reload, and targeted cache update")
    void testUpdateRouteTriggersEvictionAndReload() {
        UUID routeId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        RouteEntry updatedEntry = new RouteEntry(routeId, serviceId, tenantId, "/demo-v2", "http://mock-upstream:8080");
        when(routeCacheLoader.loadRouteById(routeId)).thenReturn(Mono.just(updatedEntry));

        RouteChangeEvent event = new RouteChangeEvent("UPDATE", "routes", routeId, tenantId);

        StepVerifier.create(handler.handleEvent(event))
                .verifyComplete();

        verify(routeCacheLoader, times(1)).loadRouteById(routeId);
        verify(routeCache, times(1)).evict(routeId);
        verify(routeCache, times(1)).put(updatedEntry);
    }

    @Test
    @DisplayName("DELETE on routes triggers targeted cache eviction without querying the database")
    void testDeleteRouteTriggersCacheEviction() {
        UUID routeId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        RouteChangeEvent event = new RouteChangeEvent("DELETE", "routes", routeId, tenantId);

        StepVerifier.create(handler.handleEvent(event))
                .verifyComplete();

        verify(routeCache, times(1)).evict(routeId);
        verifyNoInteractions(routeCacheLoader);
    }

    @Test
    @DisplayName("UPDATE on services triggers reload of all routes for that service")
    void testUpdateServiceTriggersRoutesReload() {
        UUID serviceId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        RouteEntry entry1 = new RouteEntry(UUID.randomUUID(), serviceId, tenantId, "/s1", "http://new-upstream:8080");
        RouteEntry entry2 = new RouteEntry(UUID.randomUUID(), serviceId, tenantId, "/s2", "http://new-upstream:8080");

        when(routeCacheLoader.loadRoutesByServiceId(serviceId)).thenReturn(Mono.just(List.of(entry1, entry2)));

        RouteChangeEvent event = new RouteChangeEvent("UPDATE", "services", serviceId, tenantId);

        StepVerifier.create(handler.handleEvent(event))
                .verifyComplete();

        verify(routeCacheLoader, times(1)).loadRoutesByServiceId(serviceId);
        verify(routeCache, times(1)).evictByServiceId(serviceId);
        verify(routeCache, times(1)).put(entry1);
        verify(routeCache, times(1)).put(entry2);
    }

    @Test
    @DisplayName("DELETE on services triggers eviction of all routes associated with that service")
    void testDeleteServiceTriggersEvictByServiceId() {
        UUID serviceId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        RouteChangeEvent event = new RouteChangeEvent("DELETE", "services", serviceId, tenantId);

        StepVerifier.create(handler.handleEvent(event))
                .verifyComplete();

        verify(routeCache, times(1)).evictByServiceId(serviceId);
        verifyNoInteractions(routeCacheLoader);
    }

    @Test
    @DisplayName("plugin_configs events are handled gracefully without errors")
    void testPluginConfigsEventHandledGracefully() {
        RouteChangeEvent event = new RouteChangeEvent("INSERT", "plugin_configs", UUID.randomUUID(), UUID.randomUUID());

        StepVerifier.create(handler.handleEvent(event))
                .verifyComplete();

        verifyNoInteractions(routeCacheLoader);
        verifyNoInteractions(routeCache);
    }
}
