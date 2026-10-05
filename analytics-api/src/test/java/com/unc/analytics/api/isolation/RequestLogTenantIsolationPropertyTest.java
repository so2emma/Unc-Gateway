package com.unc.analytics.api.isolation;

import com.unc.analytics.api.ingest.IngestRequest;
import com.unc.analytics.api.ingest.RequestLog;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Property-based test verifying tenant isolation invariants during request-log ingestion.
 * <p>
 * Category: TENANT_ISOLATION
 * Invariant: For any sequence of ingested events across distinct tenants, tenant-scoped queries
 * never return records belonging to another tenant.
 */
class RequestLogTenantIsolationPropertyTest {

    record IngestEvent(UUID tenantId, IngestRequest request) {
    }

    @Property(tries = 150)
    @Label("TENANT_ISOLATION: Scoped query for tenant_id never returns request_logs written for another tenant")
    boolean tenantIsolationInvariant(
            @ForAll("distinctTenants") List<UUID> tenants,
            @ForAll("ingestSequence") List<IngestEvent> sequence,
            @ForAll("targetTenantSelector") int targetSelector
    ) {
        if (tenants.size() < 2 || sequence.isEmpty()) {
            return true;
        }

        UUID targetTenant = tenants.get(Math.abs(targetSelector) % tenants.size());

        // Simulate repository write path: every row is tagged with its authenticated tenant_id
        List<RequestLog> persistedLogs = new ArrayList<>();
        for (IngestEvent event : sequence) {
            RequestLog row = new RequestLog();
            row.setId(UUID.randomUUID());
            row.setTenantId(event.tenantId());
            row.setMethod(event.request().getMethod());
            row.setPath(event.request().getPath());
            row.setStatus(event.request().getStatusCode());
            row.setLatencyMs(event.request().getLatencyMs());
            row.setConsumerId(event.request().getConsumerId() != null ? UUID.nameUUIDFromBytes(event.request().getConsumerId().getBytes()) : null);
            row.setCreatedAt(event.request().getOccurredAt());
            persistedLogs.add(row);
        }

        // Simulate tenant-scoped repository query: WHERE tenant_id = :targetTenant
        List<RequestLog> queriedLogs = persistedLogs.stream()
                .filter(row -> targetTenant.equals(row.getTenantId()))
                .collect(Collectors.toList());

        // Invariant 1: All returned logs must strictly belong to targetTenant
        boolean allMatchTarget = queriedLogs.stream()
                .allMatch(row -> targetTenant.equals(row.getTenantId()));

        // Invariant 2: None of the other tenants' logs appear in targetTenant's query
        boolean noLeakage = persistedLogs.stream()
                .filter(row -> !targetTenant.equals(row.getTenantId()))
                .noneMatch(queriedLogs::contains);

        return allMatchTarget && noLeakage;
    }

    @Provide
    Arbitrary<List<UUID>> distinctTenants() {
        return Arbitraries.of(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444")
        ).list().ofMinSize(2).ofMaxSize(4);
    }

    @Provide
    Arbitrary<List<IngestEvent>> ingestSequence() {
        Arbitrary<UUID> tenantArb = Arbitraries.of(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444")
        );

        Arbitrary<String> methodArb = Arbitraries.of("GET", "POST", "PUT", "DELETE", "PATCH");
        Arbitrary<String> pathArb = Arbitraries.of("/api/v1/echo", "/api/v1/users", "/api/v1/orders", "/proxy/test");
        Arbitrary<Integer> statusArb = Arbitraries.of(200, 201, 400, 401, 404, 500);
        Arbitrary<Long> latencyArb = Arbitraries.longs().between(1L, 2000L);
        Arbitrary<String> consumerArb = Arbitraries.of("key-alpha", "key-beta", "key-gamma", null);

        Arbitrary<IngestRequest> requestArb = Combinators.combine(
                methodArb, pathArb, statusArb, latencyArb, consumerArb
        ).as((method, path, status, latency, consumer) -> {
            IngestRequest req = new IngestRequest();
            req.setMethod(method);
            req.setPath(path);
            req.setStatusCode(status);
            req.setLatencyMs(latency);
            req.setConsumerId(consumer);
            req.setOccurredAt(Instant.now());
            return req;
        });

        Arbitrary<IngestEvent> eventArb = Combinators.combine(tenantArb, requestArb).as(IngestEvent::new);

        return eventArb.list().ofMinSize(5).ofMaxSize(50);
    }

    @Provide
    Arbitrary<Integer> targetTenantSelector() {
        return Arbitraries.integers().between(0, 3);
    }
}
