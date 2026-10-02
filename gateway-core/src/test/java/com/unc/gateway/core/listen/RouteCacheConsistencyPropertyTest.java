package com.unc.gateway.core.listen;

import com.unc.gateway.core.cache.RouteCache;
import com.unc.gateway.core.cache.RouteCacheLoader;
import com.unc.gateway.core.cache.RouteEntry;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RouteCacheConsistencyPropertyTest {

    record RouteModel(UUID routeId, UUID serviceId, UUID tenantId, String path, String upstreamUrl) {}

    sealed interface DatabaseOp permits InsertRouteOp, UpdateRouteOp, DeleteRouteOp, UpdateServiceOp, DeleteServiceOp, PluginConfigOp {}

    record InsertRouteOp(UUID routeId, UUID serviceId, UUID tenantId, String path, String upstreamUrl) implements DatabaseOp {}
    record UpdateRouteOp(UUID routeId, String newPath) implements DatabaseOp {}
    record DeleteRouteOp(UUID routeId) implements DatabaseOp {}
    record UpdateServiceOp(UUID serviceId, String newUpstreamUrl) implements DatabaseOp {}
    record DeleteServiceOp(UUID serviceId) implements DatabaseOp {}
    record PluginConfigOp(UUID pluginId, String op) implements DatabaseOp {}

    @Property(tries = 150)
    @Label("CACHE_CONSISTENCY: in-memory RouteCache state converges to reflect the latest committed change within 1 second")
    boolean routeCacheConvergesConsistentState(
            @ForAll("operationSequences") @Size(min = 5, max = 30) List<DatabaseOp> operations
    ) {
        RouteCache routeCache = new RouteCache();
        RouteCacheLoader mockLoader = mock(RouteCacheLoader.class);
        RouteChangeEventHandler handler = new RouteChangeEventHandler(mockLoader, routeCache);

        Map<UUID, RouteModel> activeRoutes = new ConcurrentHashMap<>();
        Map<UUID, String> activeServices = new ConcurrentHashMap<>();
        Map<UUID, String> oldPaths = new HashMap<>();

        for (DatabaseOp op : operations) {
            Instant start = Instant.now();

            if (op instanceof InsertRouteOp insert) {
                activeServices.putIfAbsent(insert.serviceId(), insert.upstreamUrl());
                String upstream = activeServices.get(insert.serviceId());
                RouteModel existing = activeRoutes.get(insert.routeId());
                if (existing != null && !existing.path().equals(insert.path())) {
                    oldPaths.put(insert.routeId(), existing.path());
                }
                RouteModel model = new RouteModel(insert.routeId(), insert.serviceId(), insert.tenantId(), insert.path(), upstream);
                // In RouteCache, (path, tenantId) is unique; remove any prior route that held this key
                activeRoutes.values().removeIf(r -> r.path().equals(model.path()) && Objects.equals(r.tenantId(), model.tenantId()));
                activeRoutes.put(insert.routeId(), model);

                RouteEntry entry = new RouteEntry(model.routeId(), model.serviceId(), model.tenantId(), model.path(), model.upstreamUrl());
                when(mockLoader.loadRouteById(insert.routeId())).thenReturn(Mono.just(entry));

                RouteChangeEvent event = new RouteChangeEvent("INSERT", "routes", insert.routeId(), insert.tenantId());
                handler.handleEvent(event).block(Duration.ofSeconds(1));
            } else if (op instanceof UpdateRouteOp update) {
                RouteModel existing = activeRoutes.get(update.routeId());
                if (existing != null) {
                    oldPaths.put(update.routeId(), existing.path());
                    RouteModel updated = new RouteModel(existing.routeId(), existing.serviceId(), existing.tenantId(), update.newPath(), existing.upstreamUrl());
                    activeRoutes.values().removeIf(r -> r.path().equals(updated.path()) && Objects.equals(r.tenantId(), updated.tenantId()));
                    activeRoutes.put(update.routeId(), updated);

                    RouteEntry entry = new RouteEntry(updated.routeId(), updated.serviceId(), updated.tenantId(), updated.path(), updated.upstreamUrl());
                    when(mockLoader.loadRouteById(update.routeId())).thenReturn(Mono.just(entry));

                    RouteChangeEvent event = new RouteChangeEvent("UPDATE", "routes", update.routeId(), updated.tenantId());
                    handler.handleEvent(event).block(Duration.ofSeconds(1));
                }
            } else if (op instanceof DeleteRouteOp delete) {
                RouteModel removed = activeRoutes.remove(delete.routeId());
                if (removed != null) {
                    oldPaths.put(delete.routeId(), removed.path());
                }
                RouteChangeEvent event = new RouteChangeEvent("DELETE", "routes", delete.routeId(), removed != null ? removed.tenantId() : UUID.randomUUID());
                handler.handleEvent(event).block(Duration.ofSeconds(1));
            } else if (op instanceof UpdateServiceOp updateSvc) {
                activeServices.put(updateSvc.serviceId(), updateSvc.newUpstreamUrl());
                List<RouteEntry> updatedEntries = new ArrayList<>();
                for (Map.Entry<UUID, RouteModel> entry : activeRoutes.entrySet()) {
                    if (entry.getValue().serviceId().equals(updateSvc.serviceId())) {
                        RouteModel updated = new RouteModel(
                                entry.getValue().routeId(),
                                entry.getValue().serviceId(),
                                entry.getValue().tenantId(),
                                entry.getValue().path(),
                                updateSvc.newUpstreamUrl()
                        );
                        entry.setValue(updated);
                        updatedEntries.add(new RouteEntry(updated.routeId(), updated.serviceId(), updated.tenantId(), updated.path(), updated.upstreamUrl()));
                    }
                }
                when(mockLoader.loadRoutesByServiceId(updateSvc.serviceId())).thenReturn(Mono.just(updatedEntries));

                RouteChangeEvent event = new RouteChangeEvent("UPDATE", "services", updateSvc.serviceId(), null);
                handler.handleEvent(event).block(Duration.ofSeconds(1));
            } else if (op instanceof DeleteServiceOp deleteSvc) {
                activeServices.remove(deleteSvc.serviceId());
                List<UUID> toRemove = new ArrayList<>();
                for (Map.Entry<UUID, RouteModel> entry : activeRoutes.entrySet()) {
                    if (entry.getValue().serviceId().equals(deleteSvc.serviceId())) {
                        toRemove.add(entry.getKey());
                        oldPaths.put(entry.getKey(), entry.getValue().path());
                    }
                }
                toRemove.forEach(activeRoutes::remove);

                RouteChangeEvent event = new RouteChangeEvent("DELETE", "services", deleteSvc.serviceId(), null);
                handler.handleEvent(event).block(Duration.ofSeconds(1));
            } else if (op instanceof PluginConfigOp plugin) {
                RouteChangeEvent event = new RouteChangeEvent(plugin.op(), "plugin_configs", plugin.pluginId(), UUID.randomUUID());
                handler.handleEvent(event).block(Duration.ofSeconds(1));
            }

            Duration latency = Duration.between(start, Instant.now());
            // Convergence constraint: event processed within 1-second bound
            assertThat(latency.toMillis()).isLessThan(1000);
        }

        // INVARIANT VERIFICATION:
        // 1. Every active route in the model is resolvable in RouteCache with the latest path and upstream
        for (RouteModel active : activeRoutes.values()) {
            Optional<RouteEntry> cached = routeCache.lookup(active.path(), active.tenantId());
            if (cached.isEmpty()) {
                return false;
            }
            if (!cached.get().routeId().equals(active.routeId())) {
                return false;
            }
            if (!cached.get().upstreamUrl().equals(active.upstreamUrl())) {
                return false;
            }
        }

        // 2. No old paths that are no longer active are resolvable
        for (Map.Entry<UUID, String> oldEntry : oldPaths.entrySet()) {
            UUID routeId = oldEntry.getKey();
            String oldPath = oldEntry.getValue();
            if (!activeRoutes.containsKey(routeId) || !activeRoutes.get(routeId).path().equals(oldPath)) {
                // If this oldPath is not used by another active route with the same path, it must not resolve to routeId
                Optional<RouteEntry> lookup = routeCache.lookup(oldPath);
                if (lookup.isPresent() && lookup.get().routeId().equals(routeId)) {
                    return false;
                }
            }
        }

        return true;
    }

    @Provide
    Arbitrary<List<DatabaseOp>> operationSequences() {
        UUID service1 = UUID.fromString("11111111-0000-0000-0000-000000000001");
        UUID service2 = UUID.fromString("22222222-0000-0000-0000-000000000002");
        UUID tenant1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID tenant2 = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

        List<UUID> fixedRouteIds = List.of(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                UUID.fromString("40000000-0000-0000-0000-000000000004"),
                UUID.fromString("50000000-0000-0000-0000-000000000005")
        );

        Arbitrary<DatabaseOp> insertArb = Combinators.combine(
                Arbitraries.of(fixedRouteIds),
                Arbitraries.of(service1, service2),
                Arbitraries.of(tenant1, tenant2),
                Arbitraries.of("/api/users", "/api/orders", "/api/payments", "/demo", "/test"),
                Arbitraries.of("http://upstream-1:8080", "http://upstream-2:8080")
        ).as(InsertRouteOp::new);

        Arbitrary<DatabaseOp> updateArb = Combinators.combine(
                Arbitraries.of(fixedRouteIds),
                Arbitraries.of("/api/users-v2", "/api/orders-v2", "/demo-v2", "/test-v2")
        ).as(UpdateRouteOp::new);

        Arbitrary<DatabaseOp> deleteArb = Arbitraries.of(fixedRouteIds).map(DeleteRouteOp::new);

        Arbitrary<DatabaseOp> updateServiceArb = Combinators.combine(
                Arbitraries.of(service1, service2),
                Arbitraries.of("http://new-upstream-a:9090", "http://new-upstream-b:9090")
        ).as(UpdateServiceOp::new);

        Arbitrary<DatabaseOp> deleteServiceArb = Arbitraries.of(service1, service2).map(DeleteServiceOp::new);

        Arbitrary<DatabaseOp> pluginArb = Combinators.combine(
                Arbitraries.create(UUID::randomUUID),
                Arbitraries.of("INSERT", "UPDATE", "DELETE")
        ).as(PluginConfigOp::new);

        return Arbitraries.oneOf(insertArb, updateArb, deleteArb, updateServiceArb, deleteServiceArb, pluginArb)
                .list().ofMinSize(5).ofMaxSize(30);
    }
}
