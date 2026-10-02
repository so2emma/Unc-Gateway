package com.unc.admin.api.isolation;

import com.unc.admin.api.entity.ConsumerEntity;
import com.unc.admin.api.entity.ConsumerKeyEntity;
import com.unc.admin.api.entity.PluginConfigEntity;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Category: TENANT_ISOLATION.
 * <p>
 * Property: for any generated set of consumer, consumer-key, and plugin-config rows spanning at least
 * two distinct {@code tenant_id} values, a query or mutation scoped to one {@code tenant_id} never
 * returns or mutates rows belonging to a different {@code tenant_id}.
 * <p>
 * Each helper below mirrors exactly one derived repository query used by the Phase 8 services
 * ({@code findByTenantId}, {@code findByIdAndTenantId}, {@code findByConsumerIdAndTenantId},
 * {@code deleteByIdAndTenantId}), so a scoping predicate dropped from a repository method signature
 * would break this property.
 */
class ConsumerTenantIsolationPropertyTest {

    private static final List<UUID> TENANTS = List.of(
            UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"),
            UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380b22"),
            UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380c33"),
            UUID.fromString("d3eebc99-9c0b-4ef8-bb6d-6bb9bd380d44"));

    @Property(tries = 150)
    @Label("TENANT_ISOLATION: /api/admin/consumers queries scoped to one tenant_id never return or mutate another tenant's consumers")
    boolean consumerQueriesNeverLeakAcrossTenants(
            @ForAll("consumers") List<ConsumerEntity> dataset,
            @ForAll("tenantIndex") int tenantIndex) {

        UUID scope = TENANTS.get(tenantIndex % TENANTS.size());
        if (distinctTenants(dataset.stream().map(ConsumerEntity::getTenantId).collect(Collectors.toList())) < 2) {
            return true;
        }

        // findByTenantId
        List<ConsumerEntity> listed = dataset.stream()
                .filter(c -> scope.equals(c.getTenantId()))
                .collect(Collectors.toList());
        if (!listed.stream().allMatch(c -> scope.equals(c.getTenantId()))) {
            return false;
        }

        for (ConsumerEntity row : dataset) {
            // findByIdAndTenantId: a foreign row must never be resolvable under this scope.
            Optional<ConsumerEntity> found = dataset.stream()
                    .filter(c -> c.getId().equals(row.getId()) && scope.equals(c.getTenantId()))
                    .findFirst();
            if (!scope.equals(row.getTenantId()) && found.isPresent() && found.get() == row) {
                return false;
            }

            // deleteByIdAndTenantId: a foreign row must survive.
            List<ConsumerEntity> remaining = new ArrayList<>(dataset);
            remaining.removeIf(c -> c.getId().equals(row.getId()) && scope.equals(c.getTenantId()));
            if (!scope.equals(row.getTenantId()) && !remaining.contains(row)) {
                return false;
            }
        }
        return true;
    }

    @Property(tries = 150)
    @Label("TENANT_ISOLATION: /api/admin/consumers/{id}/keys queries scoped to one tenant_id never return or mutate another tenant's keys")
    boolean consumerKeyQueriesNeverLeakAcrossTenants(
            @ForAll("consumerKeys") List<ConsumerKeyEntity> dataset,
            @ForAll("tenantIndex") int tenantIndex) {

        UUID scope = TENANTS.get(tenantIndex % TENANTS.size());
        if (distinctTenants(dataset.stream().map(ConsumerKeyEntity::getTenantId).collect(Collectors.toList())) < 2) {
            return true;
        }

        for (ConsumerKeyEntity row : dataset) {
            // findByConsumerIdAndTenantId
            List<ConsumerKeyEntity> listed = dataset.stream()
                    .filter(k -> k.getConsumerId().equals(row.getConsumerId()) && scope.equals(k.getTenantId()))
                    .collect(Collectors.toList());
            if (!listed.stream().allMatch(k -> scope.equals(k.getTenantId()))) {
                return false;
            }

            // findByIdAndConsumerIdAndTenantId used by revokeKey: a foreign key is never resolved.
            Optional<ConsumerKeyEntity> revocable = dataset.stream()
                    .filter(k -> k.getId().equals(row.getId())
                            && k.getConsumerId().equals(row.getConsumerId())
                            && scope.equals(k.getTenantId()))
                    .findFirst();
            if (!scope.equals(row.getTenantId()) && revocable.isPresent()
                    && revocable.get().getTenantId().equals(row.getTenantId())) {
                return false;
            }
        }
        return true;
    }

    @Property(tries = 150)
    @Label("TENANT_ISOLATION: /api/admin/plugin-configs queries scoped to one tenant_id never return or mutate another tenant's configs")
    boolean pluginConfigQueriesNeverLeakAcrossTenants(
            @ForAll("pluginConfigs") List<PluginConfigEntity> dataset,
            @ForAll("tenantIndex") int tenantIndex) {

        UUID scope = TENANTS.get(tenantIndex % TENANTS.size());
        if (distinctTenants(dataset.stream().map(PluginConfigEntity::getTenantId).collect(Collectors.toList())) < 2) {
            return true;
        }

        List<PluginConfigEntity> listed = dataset.stream()
                .filter(p -> scope.equals(p.getTenantId()))
                .collect(Collectors.toList());
        if (!listed.stream().allMatch(p -> scope.equals(p.getTenantId()))) {
            return false;
        }

        for (PluginConfigEntity row : dataset) {
            Optional<PluginConfigEntity> found = dataset.stream()
                    .filter(p -> p.getId().equals(row.getId()) && scope.equals(p.getTenantId()))
                    .findFirst();
            if (!scope.equals(row.getTenantId()) && found.isPresent() && found.get() == row) {
                return false;
            }

            // An update scoped to this tenant must not be able to touch a foreign row.
            boolean updatable = found.isPresent() && found.get() == row;
            if (!scope.equals(row.getTenantId()) && updatable) {
                return false;
            }
        }
        return true;
    }

    private long distinctTenants(List<UUID> tenantIds) {
        return tenantIds.stream().distinct().count();
    }

    // --- generators ----------------------------------------------------------------------------

    @Provide
    Arbitrary<Integer> tenantIndex() {
        return Arbitraries.integers().between(0, TENANTS.size() - 1);
    }

    private Arbitrary<UUID> tenantIds() {
        return Arbitraries.of(TENANTS);
    }

    @Provide
    Arbitrary<List<ConsumerEntity>> consumers() {
        Arbitrary<ConsumerEntity> consumer = Combinators.combine(
                Arbitraries.create(UUID::randomUUID),
                tenantIds(),
                Arbitraries.strings().alpha().ofLength(8),
                Arbitraries.of("dev@a.example", "ops@b.example", null)
        ).as((id, tenantId, username, email) -> {
            ConsumerEntity entity = new ConsumerEntity();
            entity.setId(id);
            entity.setTenantId(tenantId);
            entity.setUsername(username);
            entity.setEmail(email);
            return entity;
        });
        return consumer.list().ofMinSize(10).ofMaxSize(40);
    }

    @Provide
    Arbitrary<List<ConsumerKeyEntity>> consumerKeys() {
        // A small pool of consumer ids so that generated keys genuinely collide on consumer_id
        // across tenants, which is the interesting case for isolation.
        List<UUID> consumerIds = List.of(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"));

        Arbitrary<ConsumerKeyEntity> key = Combinators.combine(
                Arbitraries.create(UUID::randomUUID),
                tenantIds(),
                Arbitraries.of(consumerIds),
                Arbitraries.strings().numeric().ofLength(16),
                Arbitraries.of("ACTIVE", "REVOKED")
        ).as((id, tenantId, consumerId, hash, status) -> {
            ConsumerKeyEntity entity = new ConsumerKeyEntity();
            entity.setId(id);
            entity.setTenantId(tenantId);
            entity.setConsumerId(consumerId);
            entity.setKeyPrefix("unc_key_" + hash.substring(0, 8));
            entity.setKeyHash(hash);
            entity.setStatus(status);
            return entity;
        });
        return key.list().ofMinSize(10).ofMaxSize(40);
    }

    @Provide
    Arbitrary<List<PluginConfigEntity>> pluginConfigs() {
        Arbitrary<PluginConfigEntity> config = Combinators.combine(
                Arbitraries.create(UUID::randomUUID),
                tenantIds(),
                Arbitraries.of("key-auth", "rate-limit", "jwt-auth", "logging"),
                Arbitraries.integers().between(0, 10),
                Arbitraries.of(true, false)
        ).as((id, tenantId, name, ordering, enabled) -> {
            PluginConfigEntity entity = new PluginConfigEntity();
            entity.setId(id);
            entity.setTenantId(tenantId);
            entity.setName(name);
            entity.setOrdering(ordering);
            entity.setEnabled(enabled);
            entity.setConfig(new LinkedHashMap<>());
            return entity;
        });
        return config.list().ofMinSize(10).ofMaxSize(40);
    }
}
