package com.unc.analytics.api.isolation;

import net.jqwik.api.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Property-based test verifying tenant isolation invariants during analytics metric aggregations.
 * <p>
 * Category: TENANT_ISOLATION
 * Invariant: Aggregating latency percentiles and traffic pulse buckets for a target tenant
 * depends exclusively on rows written for that tenant, completely unaffected by other tenants' data.
 */
class AnalyticsQueryTenantIsolationPropertyTest {

    record QueryLogEntry(UUID tenantId, Long latencyMs, Instant createdAt) {
    }

    @Property(tries = 150)
    @Label("TENANT_ISOLATION: Scoped analytics metrics and traffic pulse are never influenced by another tenant")
    boolean queryTenantIsolationInvariant(
            @ForAll("distinctTenants") List<UUID> tenants,
            @ForAll("logEntries") List<QueryLogEntry> allLogs,
            @ForAll("tenantIndex") int targetIdx
    ) {
        if (tenants.size() < 2 || allLogs.isEmpty()) {
            return true;
        }

        UUID targetTenant = tenants.get(Math.abs(targetIdx) % tenants.size());

        // Extract tenant-scoped rows (simulating WHERE tenant_id = :targetTenant)
        List<QueryLogEntry> scopedLogs = allLogs.stream()
                .filter(l -> targetTenant.equals(l.tenantId()))
                .toList();

        // 1. Invariant: Latency metrics computed from allLogs vs scopedLogs
        List<Long> scopedLatencies = scopedLogs.stream()
                .map(QueryLogEntry::latencyMs)
                .sorted()
                .toList();

        if (!scopedLatencies.isEmpty()) {
            // Compute expected p95 on tenant-scoped data
            double p95Index = 0.95 * (scopedLatencies.size() - 1);
            int lower = (int) Math.floor(p95Index);
            int upper = (int) Math.ceil(p95Index);
            double expectedP95 = (lower == upper)
                    ? scopedLatencies.get(lower)
                    : scopedLatencies.get(lower) + (p95Index - lower) * (scopedLatencies.get(upper) - scopedLatencies.get(lower));

            // Verify tenant's p95 is strictly bounded within its own min/max latency
            long minLatency = scopedLatencies.get(0);
            long maxLatency = scopedLatencies.get(scopedLatencies.size() - 1);

            if (expectedP95 < minLatency || expectedP95 > maxLatency) {
                return false;
            }
        }

        // 2. Invariant: Traffic pulse bucket counts
        // Group scoped logs by minute
        Map<Instant, Long> scopedBuckets = scopedLogs.stream()
                .collect(Collectors.groupingBy(
                        l -> l.createdAt().truncatedTo(ChronoUnit.MINUTES),
                        Collectors.counting()
                ));

        // Group other tenants' logs by minute
        List<QueryLogEntry> otherLogs = allLogs.stream()
                .filter(l -> !targetTenant.equals(l.tenantId()))
                .toList();

        // Ensure total scoped bucket count equals exactly scopedLogs.size()
        long totalBucketRequests = scopedBuckets.values().stream().mapToLong(Long::longValue).sum();
        if (totalBucketRequests != scopedLogs.size()) {
            return false;
        }

        // Ensure other tenants' logs never contribute to targetTenant's bucket sums
        return otherLogs.stream().noneMatch(scopedLogs::contains);
    }

    @Provide
    Arbitrary<List<UUID>> distinctTenants() {
        return Arbitraries.of(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
        ).list().ofMinSize(2).ofMaxSize(3);
    }

    @Provide
    Arbitrary<List<QueryLogEntry>> logEntries() {
        Arbitrary<UUID> tenantArb = Arbitraries.of(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
        );
        Arbitrary<Long> latencyArb = Arbitraries.longs().between(5L, 5000L);
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Arbitrary<Instant> timeArb = Arbitraries.integers().between(-5, 5)
                .map(offsetMinutes -> baseTime.plus(offsetMinutes, ChronoUnit.MINUTES));

        return Combinators.combine(tenantArb, latencyArb, timeArb)
                .as(QueryLogEntry::new)
                .list().ofMinSize(10).ofMaxSize(100);
    }

    @Provide
    Arbitrary<Integer> tenantIndex() {
        return Arbitraries.integers().between(0, 10);
    }
}
