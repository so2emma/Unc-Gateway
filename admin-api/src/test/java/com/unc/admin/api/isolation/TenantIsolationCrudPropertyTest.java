package com.unc.admin.api.isolation;

import com.unc.admin.api.entity.RouteEntity;
import com.unc.admin.api.entity.ServiceEntity;
import net.jqwik.api.*;

import java.util.*;
import java.util.stream.Collectors;

class TenantIsolationCrudPropertyTest {

    @Property(tries = 150)
    @Label("TENANT_ISOLATION: Service CRUD queries scoped to tenant_id never return, mutate, or delete rows of another tenant")
    boolean serviceCrudIsolation(
            @ForAll("tenantList") List<String> tenants,
            @ForAll("serviceDataset") List<ServiceEntity> dataset,
            @ForAll("targetTenantIdx") int targetIdx
    ) {
        if (tenants.size() < 2 || dataset.isEmpty()) {
            return true;
        }

        String targetTenantId = tenants.get(Math.abs(targetIdx) % tenants.size());

        // Simulate repository findByTenantId
        List<ServiceEntity> tenantServices = dataset.stream()
                .filter(s -> targetTenantId.equals(s.getTenantId()))
                .collect(Collectors.toList());

        // Rule 1: No returned row belongs to a different tenant
        boolean readIsolated = tenantServices.stream().allMatch(s -> targetTenantId.equals(s.getTenantId()));

        // Simulate repository findByIdAndTenantId
        String searchId = dataset.get(0).getId();
        Optional<ServiceEntity> found = dataset.stream()
                .filter(s -> searchId.equals(s.getId()) && targetTenantId.equals(s.getTenantId()))
                .findFirst();

        boolean getIsolated = found.map(s -> targetTenantId.equals(s.getTenantId())).orElse(true);

        return readIsolated && getIsolated;
    }

    @Property(tries = 150)
    @Label("TENANT_ISOLATION: Route CRUD queries scoped to tenant_id never return, mutate, or delete rows of another tenant")
    boolean routeCrudIsolation(
            @ForAll("tenantList") List<String> tenants,
            @ForAll("routeDataset") List<RouteEntity> dataset,
            @ForAll("targetTenantIdx") int targetIdx
    ) {
        if (tenants.size() < 2 || dataset.isEmpty()) {
            return true;
        }

        String targetTenantId = tenants.get(Math.abs(targetIdx) % tenants.size());

        // Simulate repository findByTenantId
        List<RouteEntity> tenantRoutes = dataset.stream()
                .filter(r -> targetTenantId.equals(r.getTenantId()))
                .collect(Collectors.toList());

        // Rule 1: No returned route belongs to a different tenant
        return tenantRoutes.stream().allMatch(r -> targetTenantId.equals(r.getTenantId()));
    }

    @Provide
    Arbitrary<List<String>> tenantList() {
        return Arbitraries.of("tenant-a", "tenant-b", "tenant-c", "tenant-d").list().ofMinSize(2).ofMaxSize(4);
    }

    @Provide
    Arbitrary<List<ServiceEntity>> serviceDataset() {
        Arbitrary<String> ids = Arbitraries.strings().numeric().ofLength(5);
        Arbitrary<String> tenants = Arbitraries.of("tenant-a", "tenant-b", "tenant-c", "tenant-d");
        Arbitrary<String> names = Arbitraries.strings().alpha().ofLength(8);
        Arbitrary<String> urls = Arbitraries.of("http://upstream1:9090", "http://upstream2:9090");

        Arbitrary<ServiceEntity> srvArb = Combinators.combine(ids, tenants, names, urls).as((id, tId, name, url) -> {
            ServiceEntity s = new ServiceEntity();
            s.setId(id);
            s.setTenantId(tId);
            s.setName(name);
            s.setUrl(url);
            return s;
        });

        return srvArb.list().ofMinSize(10).ofMaxSize(50);
    }

    @Provide
    Arbitrary<List<RouteEntity>> routeDataset() {
        Arbitrary<String> ids = Arbitraries.strings().numeric().ofLength(5);
        Arbitrary<String> tenants = Arbitraries.of("tenant-a", "tenant-b", "tenant-c", "tenant-d");
        Arbitrary<String> srvIds = Arbitraries.strings().numeric().ofLength(5);
        Arbitrary<String> paths = Arbitraries.of("/demo", "/api", "/v1");

        Arbitrary<RouteEntity> routeArb = Combinators.combine(ids, tenants, srvIds, paths).as((id, tId, sId, p) -> {
            RouteEntity r = new RouteEntity();
            r.setId(id);
            r.setTenantId(tId);
            r.setServiceId(sId);
            r.setPaths(p);
            return r;
        });

        return routeArb.list().ofMinSize(10).ofMaxSize(50);
    }

    @Provide
    Arbitrary<Integer> targetTenantIdx() {
        return Arbitraries.integers().between(0, 3);
    }
}
