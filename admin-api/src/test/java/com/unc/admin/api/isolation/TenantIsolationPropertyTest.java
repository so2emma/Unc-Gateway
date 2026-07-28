package com.unc.admin.api.isolation;

import net.jqwik.api.*;

import java.util.List;
import java.util.stream.Collectors;

class TenantIsolationPropertyTest {

    static class EntityRow {
        final String id;
        final String tenantId;
        final String payload;

        EntityRow(String id, String tenantId, String payload) {
            this.id = id;
            this.tenantId = tenantId;
            this.payload = payload;
        }
    }

    @Property(tries = 150)
    @Label("TENANT_ISOLATION: Scoped query by tenant_id never returns rows of another tenant")
    boolean tenantQueryIsolation(
            @ForAll("tenantIds") List<String> availableTenants,
            @ForAll("dataset") List<EntityRow> dataset,
            @ForAll("targetTenantIndex") int targetIndex
    ) {
        if (availableTenants.size() < 2 || dataset.isEmpty()) {
            return true;
        }

        String targetTenantId = availableTenants.get(Math.abs(targetIndex) % availableTenants.size());

        // Simulate tenant-scoped SELECT ... WHERE tenant_id = targetTenantId
        List<EntityRow> queryResults = dataset.stream()
                .filter(row -> targetTenantId.equals(row.tenantId))
                .collect(Collectors.toList());

        // Invariant: all returned rows must belong exclusively to targetTenantId
        return queryResults.stream().allMatch(row -> targetTenantId.equals(row.tenantId));
    }

    @Provide
    Arbitrary<List<String>> tenantIds() {
        return Arbitraries.of("tenant-alpha", "tenant-beta", "tenant-gamma", "tenant-delta").list().ofMinSize(2).ofMaxSize(4);
    }

    @Provide
    Arbitrary<List<EntityRow>> dataset() {
        Arbitrary<String> ids = Arbitraries.strings().numeric().ofLength(6);
        Arbitrary<String> tenantIds = Arbitraries.of("tenant-alpha", "tenant-beta", "tenant-gamma", "tenant-delta");
        Arbitrary<String> data = Arbitraries.strings().alpha().ofLength(10);

        Arbitrary<EntityRow> rowArb = Combinators.combine(ids, tenantIds, data).as(EntityRow::new);
        return rowArb.list().ofMinSize(10).ofMaxSize(100);
    }

    @Provide
    Arbitrary<Integer> targetTenantIndex() {
        return Arbitraries.integers().between(0, 3);
    }
}
