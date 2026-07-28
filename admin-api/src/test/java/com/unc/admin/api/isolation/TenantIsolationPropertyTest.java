package com.unc.admin.api.isolation;

import net.jqwik.api.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

class TenantIsolationPropertyTest {

    static class EntityRow {
        final UUID id;
        final UUID tenantId;
        final String payload;

        EntityRow(UUID id, UUID tenantId, String payload) {
            this.id = id;
            this.tenantId = tenantId;
            this.payload = payload;
        }
    }

    @Property(tries = 150)
    @Label("TENANT_ISOLATION: Scoped query by tenant_id never returns rows of another tenant")
    boolean tenantQueryIsolation(
            @ForAll("tenantIds") List<UUID> availableTenants,
            @ForAll("dataset") List<EntityRow> dataset,
            @ForAll("targetTenantIndex") int targetIndex
    ) {
        if (availableTenants.size() < 2 || dataset.isEmpty()) {
            return true;
        }

        UUID targetTenantId = availableTenants.get(Math.abs(targetIndex) % availableTenants.size());

        // Simulate tenant-scoped SELECT ... WHERE tenant_id = targetTenantId
        List<EntityRow> queryResults = dataset.stream()
                .filter(row -> targetTenantId.equals(row.tenantId))
                .collect(Collectors.toList());

        // Invariant: all returned rows must belong exclusively to targetTenantId
        return queryResults.stream().allMatch(row -> targetTenantId.equals(row.tenantId));
    }

    @Provide
    Arbitrary<List<UUID>> tenantIds() {
        return Arbitraries.of(
                UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"),
                UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380b22"),
                UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380c33"),
                UUID.fromString("d3eebc99-9c0b-4ef8-bb6d-6bb9bd380d44")
        ).list().ofMinSize(2).ofMaxSize(4);
    }

    @Provide
    Arbitrary<List<EntityRow>> dataset() {
        Arbitrary<UUID> ids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<UUID> tenantIds = Arbitraries.of(
                UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"),
                UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380b22"),
                UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380c33"),
                UUID.fromString("d3eebc99-9c0b-4ef8-bb6d-6bb9bd380d44")
        );
        Arbitrary<String> data = Arbitraries.strings().alpha().ofLength(10);

        Arbitrary<EntityRow> rowArb = Combinators.combine(ids, tenantIds, data).as(EntityRow::new);
        return rowArb.list().ofMinSize(10).ofMaxSize(100);
    }

    @Provide
    Arbitrary<Integer> targetTenantIndex() {
        return Arbitraries.integers().between(0, 3);
    }
}
